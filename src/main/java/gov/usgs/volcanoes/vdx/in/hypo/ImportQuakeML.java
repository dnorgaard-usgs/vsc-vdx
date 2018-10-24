package gov.usgs.volcanoes.vdx.in.hypo;

import gov.usgs.volcanoes.core.configfile.ConfigFile;
import gov.usgs.volcanoes.core.legacy.Arguments;
import gov.usgs.volcanoes.core.quakeml.Event;
import gov.usgs.volcanoes.core.quakeml.EventSet;
import gov.usgs.volcanoes.core.quakeml.Magnitude;
import gov.usgs.volcanoes.core.quakeml.Origin;
import gov.usgs.volcanoes.core.time.J2kSec;
import gov.usgs.volcanoes.core.util.ResourceReader;
import gov.usgs.volcanoes.core.util.StringUtils;
import gov.usgs.volcanoes.vdx.data.Channel;
import gov.usgs.volcanoes.vdx.data.Column;
import gov.usgs.volcanoes.vdx.data.Rank;
import gov.usgs.volcanoes.vdx.data.SQLDataSource;
import gov.usgs.volcanoes.vdx.data.SQLDataSourceDescriptor;
import gov.usgs.volcanoes.vdx.data.SQLDataSourceHandler;
import gov.usgs.volcanoes.vdx.data.hypo.Hypocenter;
import gov.usgs.volcanoes.vdx.data.hypo.SQLHypocenterDataSource;
import gov.usgs.volcanoes.vdx.in.Importer;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

/**
 * Import QuakeML files.
 *
 * @author Diana Norgaard
 */
public class ImportQuakeML implements Importer {

  private static final Logger LOGGER = LoggerFactory.getLogger(ImportQuakeML.class);
  public ResourceReader rr;

  public static Set<String> flags;
  public static Set<String> keys;

  public String vdxConfig;

  public ConfigFile params;
  public ConfigFile vdxParams;
  public ConfigFile rankParams;
  public ConfigFile columnParams;
  public ConfigFile channelParams;
  public ConfigFile dataSourceParams;
  public ConfigFile translationParams;

  public String driver;
  public String prefix;
  public String url;

  public SimpleDateFormat dateIn;
  public SimpleDateFormat dateOut;
  public Date date;
  public Double j2ksec;

  public String filenameMask;
  public int headerLines;

  public String importColumns;
  public String[] importColumnArray;
  public Map<Integer, String> importColumnMap;

  public String dataSource;
  public SQLHypocenterDataSource sqlDataSource;
  public SQLDataSourceHandler sqlDataSourceHandler;
  public SQLDataSourceDescriptor sqlDataSourceDescriptor;
  public List<String> dataSourceList;
  public Iterator<String> dsIterator;
  public Map<String, SQLDataSource> sqlDataSourceMap;
  public Map<String, String> dataSourceColumnMap;
  public Map<String, String> dataSourceChannelMap;
  public Map<String, Integer> dataSourceRankMap;

  public Rank rank;
  public String rankName;
  public int rankValue;
  public int rankDefault;
  public int rid;

  public String channels;
  public String[] channelArray;
  public Map<String, Channel> channelMap;
  public Channel channel;
  public String channelCode;
  public String channelName;
  public double channelLon;
  public double channelLat;
  public double channelHeight;
  public List<String> channelList;
  public Iterator<String> chIterator;
  public String defaultChannels;

  public String columns;
  public String[] columnArray;
  public HashMap<String, Column> columnMap;
  public Column column;
  public String columnName;
  public String columnDescription;
  public String columnUnit;
  public int columnIdx;
  public boolean columnActive;
  public boolean columnChecked;
  public List<String> columnList;
  public Iterator<String> coIterator;
  public String defaultColumns;

  public String importerType = "hypocenters";

  static {
    flags = new HashSet<String>();
    keys = new HashSet<String>();
    keys.add("-c");
    flags.add("-h");
    flags.add("-v");
  }

  /**
   * takes a config file as a parameter and parses it to prepare for importing.
   *
   * @param importerClass name of importer class
   * @param configFile configuration file
   * @param verbose true for info, false for severe
   */
  public void initialize(String importerClass, String configFile, boolean verbose) {

    // initialize the logger for this importer
    LOGGER.info("ImportQuakeML.initialize() succeeded.");

    // process the config file
    processConfigFile(configFile);
  }

  /**
   * disconnects from the database.
   */
  public void deinitialize() {
    sqlDataSource.disconnect();
  }

  /**
   * Parse configuration file.  This sets class variables used in the importing process
   *
   * @param configFile name of the config file
   */
  public void processConfigFile(String configFile) {

    LOGGER.info("Reading config file {}", configFile);

    // initialize the config file and verify that it was read
    params = new ConfigFile(configFile);
    if (!params.wasSuccessfullyRead()) {
      LOGGER.error("{} was not successfully read", configFile);
      System.exit(-1);
    }

    // get the vdx parameter, and exit if it's missing
    vdxConfig = StringUtils.stringToString(params.getString("vdx.config"), "VDX.config");
    if (vdxConfig == null) {
      LOGGER.error("vdx.config parameter missing from config file");
      System.exit(-1);
    }

    // get the vdx config as it's own config file object
    vdxParams = new ConfigFile(vdxConfig);
    driver = vdxParams.getString("vdx.driver");
    url = vdxParams.getString("vdx.url");
    prefix = vdxParams.getString("vdx.prefix");

    // define the data source handler that acts as a wrapper for data sources
    sqlDataSourceHandler = new SQLDataSourceHandler(driver, url, prefix);

    // get the list of data sources that are being used in this import
    dataSource = params.getString("dataSource");

    // lookup the data source from the list that is in vdxSources.config
    sqlDataSourceDescriptor = sqlDataSourceHandler.getDataSourceDescriptor(dataSource);
    if (sqlDataSourceDescriptor == null) {
      LOGGER.error("{} sql data source does not exist in vdxSources.config", dataSource);
    }

    // formally get the data source from the list of descriptors.
    // his will initialize the data source which includes db creation
    sqlDataSource = (SQLHypocenterDataSource) sqlDataSourceDescriptor.getSQLDataSource();

    if (!sqlDataSource.getType().equals(importerType)) {
      LOGGER.error("dataSource not a {} data source", importerType);
      System.exit(-1);
    }

    // information related to the time stamps
    dateIn = new SimpleDateFormat(
        StringUtils.stringToString(params.getString("timestamp"), "yyyyMMddHHmmssSS"));
    dateIn.setTimeZone(
        TimeZone.getTimeZone(StringUtils.stringToString(params.getString("timezone"), "GMT")));

    // get the list of ranks that are being used in this import
    rankParams = params.getSubConfig("rank");
    rankName = StringUtils.stringToString(rankParams.getString("name"), "Raw Data");
    rankValue = StringUtils.stringToInt(rankParams.getString("value"), 1);
    rankDefault = StringUtils.stringToInt(rankParams.getString("default"), 0);
    rank = new Rank(0, rankName, rankValue, rankDefault);

    // create rank entry
    if (sqlDataSource.getRanksFlag()) {
      Rank tempRank = sqlDataSource.defaultGetRank(rank);
      if (tempRank == null) {
        tempRank = sqlDataSource.defaultInsertRank(rank);
      }
      if (tempRank == null) {
        LOGGER.error("invalid rank for dataSource {}", dataSource);
        System.exit(-1);
      }
      rid = tempRank.getId();
    }
  }

  /**
   * Parse QuakeML file from url (resource locator or file name).
   */
  public void process(String resource) {

    ResourceReader rr = ResourceReader.getResourceReader(resource);
    if (rr == null) {
      LOGGER.error("skipping: {} (resource is invalid)", resource);
      return;
    }
    EventSet eventSet = null;
    try {
      LOGGER.info("importing: {}", resource);
      eventSet = EventSet.parseQuakeml(rr.getInputStream());
    } catch (IOException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    } catch (ParserConfigurationException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    } catch (SAXException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
    if (eventSet == null || eventSet.size() == 0) {
      return;
    }

    int result;
    for (Event e : eventSet.values()) {
      // Magnitude
      Magnitude m = e.getPreferredMagnitude();
      if(m == null){
        continue;    // preferred magnitude required
      }
      // Origin
      Origin o = e.getPreferredOrigin();
      if(o == null){
        continue;
      }
      long time = o.getTime();
      double latitude = o.getLatitude();
      double longitude = o.getLongitude();
      double depth = o.getDepth()/1000; // needs to be in km
      // Hypocenter
      Hypocenter hc = new Hypocenter(J2kSec.fromEpoch(time), rid, latitude, longitude, depth, m.getMagnitude().getValue());
      hc.eid=e.getEventId();
      hc.azgap=(int) o.getQuality().getAzimuthalGap();
      hc.nphases=o.getQuality().getUsedPhaseCount();
      hc.rms=o.getQuality().getStandardError();
      hc.dmin=o.getQuality().getMinimumDistance();
      hc.magtype=(m.getType()==null?null:m.getType().substring(1,1).toUpperCase());
      result = sqlDataSource.insertHypocenter(hc);
      LOGGER.info("{}:{}", result, hc.toString());
    }    
       
    rr.close();
  }

  /**
   * Print instructions.
   *
   * @param importerClass name of importer class
   * @param message instructions
   */
  public void outputInstructions(String importerClass, String message) {
    if (message == null) {
      System.err.println(message);
    }
    System.err.println(importerClass + " -c configfile filelist");
  }

  /**
   * Main method. Command line syntax: -h, --help print help message -c config file name -v verbose
   * mode files ...
   *
   * @param as command line args
   */
  public static void main(String[] as) {

    ImportQuakeML importer = new ImportQuakeML();

    Arguments args = new Arguments(as, flags, keys);

    if (args.flagged("-h")) {
      importer.outputInstructions(importer.getClass().getName(), null);
      System.exit(-1);
    }

    if (!args.contains("-c")) {
      importer.outputInstructions(importer.getClass().getName(), "Config file required");
      System.exit(-1);
    }

    importer.initialize(importer.getClass().getName(), args.get("-c"), args.flagged("-v"));

    List<String> files = args.unused();
    for (String file : files) {
      importer.process(file);
    }

    importer.deinitialize();
  }
}
