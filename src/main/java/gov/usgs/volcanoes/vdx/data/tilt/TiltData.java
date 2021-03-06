package gov.usgs.volcanoes.vdx.data.tilt;

import cern.colt.function.DoubleDoubleFunction;
import cern.colt.matrix.DoubleFactory2D;
import cern.colt.matrix.DoubleMatrix1D;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.linalg.Algebra;

import gov.usgs.volcanoes.core.data.GenericDataMatrix;

import java.util.List;

/**
 * GenericDataMatrix with 3 columns: time/east/north to store tilt data.
 *
 * @author Dan Cervelli
 * @author Loren Antolik
 */
public class TiltData extends GenericDataMatrix {

  /**
   * Default constructor.
   */
  public TiltData() {
    super();
  }

  /**
   * Constructor.
   *
   * @param pts list of raws, each of them is double[3]
   */
  public TiltData(List<double[]> pts) {
    super(pts);
  }

  /**
   * Set predefined column names: time/east/north.
   */
  public void setColumnNames() {
    columnMap.put("time", 0);
    columnMap.put("rank", 1);
    columnMap.put("east", 2);
    columnMap.put("north", 3);
    columnMap.put("holeTemp", 4);
    columnMap.put("boxTemp", 5);
    columnMap.put("instVolt", 6);
    columnMap.put("rainfall", 7);
  }

  /**
   * Get the time/east/north data for a specific row.
   *
   * @param row the row number
   * @return t/e/n for that row
   */
  public double[] getTen(int row) {
    return new double[]{data.get(row, 0), data.get(row, 2), data.get(row, 3)};
  }

  /**
   * Gets time/rid/radial/tangential/east/north/magnitude/azimuth/
   * holetemp/boxtemp/instvolt/gndvolt/rain data using the specified azimuth.
   *
   * @param theta azimuth in degrees
   * @return the Nx7 data matrix
   */
  public DoubleMatrix2D getAllData(double theta) {
    return DoubleFactory2D.dense.compose(new DoubleMatrix2D[][]
        {{data.viewPart(0, 0, data.rows(), 2),
            getRotatedDataWithoutTime(theta),
            data.viewPart(0, 2, data.rows(), 2),
            getVelocityDataWithoutTime(),
            data.viewPart(0, 4, data.rows(), 4)}});
    // data.viewPart(0, 4, data.rows(), 3),
    // getRainDataWithoutTime()}});
  }

  /**
   * Convert the rainfall values to plottable time series values.
   *
   * @return matrix of time series values
   */
  public DoubleMatrix2D getRainDataWithoutTime() {

    DoubleMatrix2D rain = DoubleFactory2D.dense.make(data.rows(), 1, Double.NaN);

    double total = 0;
    double r = 0;
    double last = data.getQuick(0, 7);

    // set the initial amount of rainfall to be zero for this time period
    rain.setQuick(0, 0, 0);

    // iterate through all subsequent rows and assign a rainfall amount if the
    // data increases.  Keep the total of the rainfall is less than the previous reading
    for (int i = 1; i < data.rows(); i++) {
      r = data.getQuick(i, 7);
      if (!Double.isNaN(r)) {
        if (r < last) {
          last = 0;
        }
        total += (r - last);
        last = r;
        rain.setQuick(i, 0, total);
      }
    }
    return rain;
  }

  /**
   * Gets radial/tangential data using the specified azimuth.
   *
   * @param theta the azimuth in degrees
   * @return the Nx2 data matrix
   */
  public DoubleMatrix2D getRotatedDataWithoutTime(double theta) {
    DoubleMatrix2D en = data.viewPart(0, 2, data.rows(), 2);

    // this returns a 2D matrix with tangential as first column and radial as second column
    DoubleMatrix2D tr = Algebra.DEFAULT.mult(en, getRotationMatrix(theta));

    // flip the radial and tangential columns so radial is
    // first column and tangential is second column
    return DoubleFactory2D.dense.compose(
        new DoubleMatrix2D[][]{{tr.viewPart(0, 1, tr.rows(), 1), tr.viewPart(0, 0, tr.rows(), 1)}});
  }

  /**
   * Gets time/radial/tangential data using the specified azimuth.
   *
   * @param theta the azimuth in degrees
   * @return the Nx3 data matrix
   */
  public DoubleMatrix2D getRotatedData(double theta) {
    DoubleMatrix2D rt = getRotatedDataWithoutTime(theta);
    return DoubleFactory2D.dense
        .compose(new DoubleMatrix2D[][]{{data.viewPart(0, 0, data.rows(), 1), rt}});
  }

  /**
   * Gets magnitude/azimuth data.
   *
   * @return the Nx2 data matrix
   */
  public DoubleMatrix2D getVelocityDataWithoutTime() {
    final DoubleMatrix2D en = data.viewPart(0, 2, data.rows(), 2);
    DoubleMatrix2D ma = en.copy();
    DoubleMatrix1D m = ma.viewColumn(0);
    m.assign(ma.viewColumn(1), new DoubleDoubleFunction() {
          private double ox = en.get(0, 0);
          private double oy = en.get(0, 1);

          public double apply(double x, double y) {
            return Math.sqrt((x - ox) * (x - ox) + (y - oy) * (y - oy));
          }
        }
    );
    DoubleMatrix1D a = ma.viewColumn(1);
    a.assign(en.viewColumn(0), new DoubleDoubleFunction() {
          private double ox = en.get(0, 0);
          private double oy = en.get(0, 1);

          public double apply(double y, double x) {
            return Math.atan2(y - oy, x - ox);
          }
        }
    );
    return ma;
  }

  /**
   * Gets time/magnitude/azimuth data.
   *
   * @return the Nx3 data
   */
  public DoubleMatrix2D getVelocityData() {
    DoubleMatrix2D ma = getVelocityDataWithoutTime();
    return DoubleFactory2D.dense
        .compose(new DoubleMatrix2D[][]{{data.viewPart(0, 0, data.rows(), 1), ma}});
  }

  /**
   * Gets a rotation matrix for the specified azimuth.
   *
   * @param theta the azimuth in degrees
   * @return the 2x2 rotation matrix
   */
  private DoubleMatrix2D getRotationMatrix(double theta) {
    DoubleMatrix2D rm = DoubleFactory2D.dense.make(2, 2);
    double tr = Math.toRadians(theta);
    rm.setQuick(0, 0, Math.cos(tr));
    rm.setQuick(0, 1, Math.sin(tr));
    rm.setQuick(1, 0, -Math.sin(tr));
    rm.setQuick(1, 1, Math.cos(tr));
    return rm;
  }

  /**
   * Gets the optimal azimuth for placing the most tilt in the radial direction.
   *
   * @return the optimal azimuth in degrees
   */
  public double getOptimalAzimuth() {
    if (data.rows() <= 1) {
      return 0;
    }

    DoubleMatrix2D matrix = DoubleFactory2D.dense.compose(new DoubleMatrix2D[][]{{
        data.viewPart(0, 0, data.rows(), 1),
        DoubleFactory2D.dense.make(data.rows(), 1, 1.0)
      }}
    );
    DoubleMatrix2D matrixInv = Algebra.DEFAULT.mult(Algebra.DEFAULT.inverse(
        Algebra.DEFAULT.mult(matrix.viewDice(), matrix)), matrix.viewDice());
    double minR = Double.MAX_VALUE;
    double minTheta = 0;

    for (double theta = 0.0; theta <= 360.0; theta += 1.0) {
      DoubleMatrix2D rm = getRotationMatrix(theta);
      DoubleMatrix2D d = Algebra.DEFAULT.mult(rm,
          data.viewPart(0, 2, data.rows(), 2).viewDice());
      DoubleMatrix2D subD = d.viewPart(0, 0, 1, d.columns()).viewDice();
      DoubleMatrix2D m = Algebra.DEFAULT.mult(matrixInv, subD);
      DoubleMatrix2D r = subD.assign(Algebra.DEFAULT.mult(matrix, m),
          cern.jet.math.Functions.minus);
      r = Algebra.DEFAULT.mult(r.viewDice(), r);
      double val = r.get(0, 0);
      if (val < minR) {
        minR = val;
        minTheta = theta;
      }
    }
    minTheta = minTheta % 360.0;
    minTheta = 360 - minTheta;
    return minTheta;
  }
}
