package gov.usgs.vdx.in.conn;

import gov.usgs.util.ConfigFile;
import gov.usgs.util.Util;

import java.text.*;

/**
 * An extension of IPConnection for dealing with FreeWave communication over IP.
 *
 * @author Dan Cervelli, Ralf Krug, Loren Antolik
 * @version 1.3
 */
public class FreewaveIPConnection extends IPConnection implements Connection {
	
	/** number of milliseconds before a timeout waiting for an OK */
	private static final int WAIT4OK_TIMEOUT = 2000;
	
	/** the call number of the freewave */
	protected int callnumber;
	
	/** the repeater entry for the freewave */
	protected int repeater;
	
	/** a number format for properly dialing the radio phone number */
	private DecimalFormat radioNumberFormatter;
	
	/** 
	 * default constructor
	 */
	public FreewaveIPConnection() {
		super();
	}
	
	/**
	 * constructor
	 * @param name thread name
	 */
	public FreewaveIPConnection(String name) {
		super(name);
	}
	
	/**
	 * Initialize FreewaveIPConnection
	 */
	public void initialize(ConfigFile params) throws Exception {
		super.initialize(params);
		callnumber				= Util.stringToInt(params.getString("callnumber"));
		repeater				= Util.stringToInt(params.getString("repeater"));
		radioNumberFormatter	= new DecimalFormat ("#######");
	}
	
	/**
	 * Get settings
	 */
	public String toString() {
		String settings	= super.toString();
		settings	   += "callnumber:" + callnumber + "/";
		settings	   += "repeater:" + repeater + "/";
		return settings;
	}
	
	/** 
	 * Connects to a FreeWave.
	 * @param repeaterEntry the repeater entry number in the FreeWave callbook
	 * @param radioNumber the radio phone number
	 * @param timeout the timeout in milliseconds (-1 == none)
	 * @throws Exception various exceptions can be thrown with different messages depending on the outcome
	 */
	public void connect() throws Exception {
		super.connect();		 
		setRepeater(repeater);
		call(callnumber);
	}
	
	/** 
	 * Calls the FreeWave.
	 * @param radioNumber the radio phone number
	 * @param establishConnectionTimeout the timeout (ms)
	 */
	private void call(int radioNumber) throws Exception {
		String cmd = "ATD" + radioNumberFormatter.format(radioNumber);
		writeString(cmd);
		
		// throws FreewaveConnectionException if no 'OK' answer
		wait4OK();
		
		// throws FreewaveConnectionException if no 'CONNECT' answer
		wait4Connect(timeout);
	}
	
	/** 
	 * Sets the repeater path.
	 * @param repeaterEntry the repeater entry in the FreeWave phone book
	 */
	private void setRepeater(int repeater) throws Exception {
		String cmd = "ATXC" + (char)('0' + repeater);
		writeString(cmd);
		wait4OK();
	}
	
	/** 
	 * Waits for an OK from the FreeWave.
	 * @throws Exception if there was a problem
	 */
	private void wait4OK() throws Exception {
		
		// throws SerialConnectionException if timeout
		String msg = readString(WAIT4OK_TIMEOUT);

		if (0 != msg.indexOf ("OK"))
			throw new Exception("'OK' expected but '" + msg + "' received");

		//sometimes "CONNECT" comes immediately after "OK", if so put the rest back to the message queue
		int idx = msg.indexOf("CONNECT");
		if (-1 != idx) {
			msgQueue.add(msg.substring(idx));
			// writeString(msg.substring(idx));
		}
	}
	
	/** 
	 * Waits for a CONNECT from the FreeWave.
	 * @param establishConnectionTimeout the timeout (ms) (-1 == none)
	 * @throws Exception if there was a problem
	 */
	private void wait4Connect (int timeout) throws Exception {
		
		// throws SerialConnectionException if timeout
		String msg = readString((-1 == timeout) ? -1 : (timeout));

		if (0 != msg.indexOf ("CONNECT")) {
			throw new Exception ("'CONNECT' expected but '" + msg + "' received");
		}
	}
	
	/**
	 * Waits <code>timeout</code>sec for a message.
	 * Gets a message from the internal message queue
	 * <p>
	 * In case <code>CCSAILMode</code> is high, this method behaves different:
	 * It waits for a complete CCSAIL-String.
	 * The CCSAIL string is complete, when the last char of a string is 0x03.
	 * The CCSAIL string might arrive in one or more single messages.
	 * Additionally the CD line is watched to see if the connection broke
	 *
	 * @param timeout the time (in milliseconds) to wait till a message is put into the queue
	 *		if timeout == -1, then no timeout, but wait for user interaction
	 * @exception Exception in case of timeout or broken connection.
	 *
	 * @return the oldest message from the queue
	 */
	/*
	public String readString (Device device, long timeout) throws Exception {
		if (!open)
			throw new Exception("Connection not open.");
			
		long start = System.currentTimeMillis();
		long end   = start + timeout;
		long now   = start;
		long delay = 10; //receiveTimeout;

		if ((timeout > 0) && (timeout < delay)) 
			delay = timeout;

		StringBuffer sb = new StringBuffer();
		while ( (now < end) || (-1L == timeout) ) {
			if (!lockQueue) {
				if (!msgQueue.isEmpty()) {
					sb.append(msgQueue.firstElement());
					msgQueue.removeElementAt (0);
					
					// if we are talking to the device attached to the radio
					if (device != null) {
						if (device.messageCompleted(sb)) {
							return sb.toString();
						}
					} else {
						return sb.toString();
					}

					// In CCSAIL mode the last char of a message must be (char)3
					// if (!CCSAILMode || ((char)3 == sb.charAt (sb.length() - 1)) )
						// return sb.toString();
				}
			}

			try {
				Thread.sleep (delay);
			} catch (InterruptedException e) {

			}

			now = System.currentTimeMillis();

		}

		String txt = "Timeout while waiting for data.";
		if (sb.length() > 0) {
			txt += " Already received: ";
			txt += sb.toString();
		}
		throw new Exception(txt);
	}
	*/

	/** 
	 * Puts a message into the message queue.
	 * @param msg the message
	 * @return success state
	 */	
	/*
	public boolean writeString (String msg) {
		if (!open) return false;

		 // is a new message arriving?
		if (lockQueue) {
			// wait till the end of the receive timeout
			try {
				Thread.sleep(receiveTimeout);
			} catch (InterruptedException e) {}

			// if the queue is still locked, something is going wrong...
			if (lockQueue) return false;
		}

		// place the message in the queue
		lockQueue = true;
		msgQueue.insertElementAt(new String (msg), 0);
		lockQueue = false;

		return true;
	}
	*/
}