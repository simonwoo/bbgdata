package BBGTick;
// -sd 2012-11-01T05:30:00 -ed 2012-11-02T20:30:00

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

import com.bloomberglp.blpapi.Datetime;
import com.bloomberglp.blpapi.Event;
import com.bloomberglp.blpapi.Element;
import com.bloomberglp.blpapi.Message;
import com.bloomberglp.blpapi.MessageIterator;
import com.bloomberglp.blpapi.Name;
import com.bloomberglp.blpapi.SessionOptions;
import com.bloomberglp.blpapi.Request;
import com.bloomberglp.blpapi.Service;
import com.bloomberglp.blpapi.Session;
import java.util.Calendar;
import java.io.PrintWriter;

public class BBGRawTick {
    private static final Name TICK_DATA      = new Name("tickData");
    private static final Name COND_CODE      = new Name("conditionCodes");
    private static final Name SIZE           = new Name("size");
    private static final Name TIME           = new Name("time");
    private static final Name TYPE           = new Name("type");
    private static final Name VALUE          = new Name("value");
    private static final Name RESPONSE_ERROR = new Name("responseError");
    private static final Name CATEGORY       = new Name("category");
    private static final Name MESSAGE        = new Name("message");

    private String            d_host;
    private int               d_port;
    //private String            d_security;
    private ArrayList<String> d_secs;
    private ArrayList<String> d_events;
    private boolean           d_conditionCodes;
    private String            d_startDateTime;
    private String            d_endDateTime;
    private SimpleDateFormat  d_dateFormat;
    private DecimalFormat     d_decimalFormat;
    private PrintWriter writer;

    public static void main(String[] args) throws Exception
    {
        BBGRawTick brt = new BBGRawTick();
        brt.run(args);
        //System.out.println("Press ENTER to quit");
        //System.in.read();
    }

    private Calendar getPreviousTradingDate()
    {
        Calendar rightNow = Calendar.getInstance();
        rightNow.roll(Calendar.DAY_OF_MONTH, -1);
        if (rightNow.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            rightNow.roll(Calendar.DAY_OF_MONTH, -2);
        }
        else if (rightNow.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) {
            rightNow.roll(Calendar.DAY_OF_MONTH, -1);
        }

        rightNow.set(Calendar.HOUR_OF_DAY, 13);
        rightNow.set(Calendar.MINUTE, 30);
        rightNow.set(Calendar.SECOND, 0);

        return rightNow;
    }


    public BBGRawTick()
    {
        d_host = "localhost";
        d_port = 8194;
        //d_security = "ATHM US Equity";//stock
        //d_security = "QZX2 Index";   //futures index
        //d_security = "GBPUSD Curncy";//forex option
        //d_security = "GOOG US 11/17/12 C680 EQUITY";//option
        //d_security = "BITA US 03/22/14 C35 Equity";
        d_secs = new ArrayList<String>();
        d_events = new ArrayList<String>();
        d_conditionCodes = false;

        d_dateFormat = new SimpleDateFormat();
        d_dateFormat.applyPattern("MM/dd/yyyy k:mm:ss");
        d_decimalFormat = new DecimalFormat();
        d_decimalFormat.setMaximumFractionDigits(3);
    }

    private void run(String[] args) throws Exception
    {
        if (!parseCommandLine(args)) return;
        SessionOptions sessionOptions = new SessionOptions();
        sessionOptions.setServerHost(d_host);
        sessionOptions.setServerPort(d_port);

        System.out.println("Connecting to " + d_host + ":" + d_port);
        Session session = new Session(sessionOptions);
        if (!session.start()) {
            System.err.println("Failed to start session.");
            return;
        }
        if (!session.openService("//blp/refdata")) {
            System.err.println("Failed to open //blp/refdata");
            return;
        }
        
        //TODO: Read stocks data into d_secs!!!
        
        for (String security : d_secs){
            writer = new PrintWriter(security+".csv", "UTF-8");
            writer.println("TIME,TYPE,VALUE,SIZE,CC");
            sendIntradayTickRequest(session, security);
            // wait for events from session.
            eventLoop(session);
            writer.close();
        }
        
        
        session.stop();
    }

    private void eventLoop(Session session) throws Exception
    {
        boolean done = false;
        while (!done) {
            Event event = session.nextEvent();
            if (event.eventType() == Event.EventType.PARTIAL_RESPONSE) {
                System.out.println("Processing Partial Response");
                processResponseEvent(event);
            }
            else if (event.eventType() == Event.EventType.RESPONSE) {
                System.out.println("Processing Response");
                processResponseEvent(event);
                done = true;
            } else {
                MessageIterator msgIter = event.messageIterator();
                while (msgIter.hasNext()) {
                    Message msg = msgIter.next();
                    System.out.println(msg.asElement());
                    if (event.eventType() == Event.EventType.SESSION_STATUS) {
                        if (msg.messageType().equals("SessionTerminated")) {
                            done = true;
                        }
                    }
                }
            }
        }
    }

    private void processMessage(Message msg) throws Exception {
        Element data = msg.getElement(TICK_DATA).getElement(TICK_DATA);
        int numItems = data.numValues();
        System.out.println("TIME\t\t\tTYPE\tVALUE\t\tSIZE\tCC");
        System.out.println("----\t\t\t----\t-----\t\t----\t--");

        for (int i = 0; i < numItems; ++i) {
            Element item = data.getValueAsElement(i);
            Datetime time = item.getElementAsDate(TIME);
            String type = item.getElementAsString(TYPE);
            double value = item.getElementAsFloat64(VALUE);
            int size = item.getElementAsInt32(SIZE);
            String cc = "";
            if (item.hasElement(COND_CODE)) {
                cc = item.getElementAsString(COND_CODE);
            }

            String t=d_dateFormat.format(time.calendar().getTime());
            String ty=type;
            String val=d_decimalFormat.format(value);
            String sz=d_decimalFormat.format(size);

            writer.println(t+","+ty+","+val+","+sz+","+cc);

            /*System.out.println(d_dateFormat.format(time.calendar().getTime()) + "\t" +
                    type + "\t" +
                    d_decimalFormat.format(value) + "\t\t" +
                    d_decimalFormat.format(size) + "\t" +
                    cc);*/
        }
    }

    private void processResponseEvent(Event event) throws Exception {
        MessageIterator msgIter = event.messageIterator();
        while (msgIter.hasNext()) {
            Message msg = msgIter.next();
            if (msg.hasElement(RESPONSE_ERROR)) {
                printErrorInfo("REQUEST FAILED: ", msg.getElement(RESPONSE_ERROR));
                continue;
            }
            processMessage(msg);
        }
    }

    private void sendIntradayTickRequest(Session session, String sec) throws Exception
    {
        Service refDataService = session.getService("//blp/refdata");
        Request request = refDataService.createRequest("IntradayTickRequest");

        request.set("security", sec);

        // Add fields to request
        Element eventTypes = request.getElement("eventTypes");
        for (String event : d_events) {
            eventTypes.appendValue(event);
        }

        if (d_startDateTime == null || d_endDateTime == null) {
            Calendar calendar = getPreviousTradingDate();
            Datetime prevTradedDate = new Datetime(calendar);
            request.set("startDateTime", prevTradedDate);
            calendar.roll(Calendar.MINUTE, +5);
            Datetime endDateTime = new Datetime(calendar);
            request.set("endDateTime", endDateTime);
        } else {
        	// All times are in GMT
        	request.set("startDateTime", d_startDateTime);
        	request.set("endDateTime", d_endDateTime);
        }

        if (d_conditionCodes) {
            request.set("includeConditionCodes", true);
        }

        System.out.println("Sending Request: " + request);
        session.sendRequest(request, null);
    }

    private boolean parseCommandLine(String[] args)
    {
        for (int i = 0; i < args.length; ++i) {
            /*if (args[i].equalsIgnoreCase("-s")) {
                d_security = args[i+1];
            }
            else */if (args[i].equalsIgnoreCase("-e")) {
                d_events.add(args[i+1]);
            }
            else if (args[i].equalsIgnoreCase("-cc")) {
                d_conditionCodes = true;
            }
            else if (args[i].equalsIgnoreCase("-sd")) {
                d_startDateTime = args[i+1];
            }
            else if (args[i].equalsIgnoreCase("-ed")) {
                d_endDateTime = args[i+1];
            }
            else if (args[i].equalsIgnoreCase("-ip")) {
                d_host = args[i+1];
            }
            else if (args[i].equalsIgnoreCase("-p")) {
                d_port = Integer.parseInt(args[i+1]);
            }
            else if (args[i].equalsIgnoreCase("-h")) {
                printUsage();
                return false;
            }
        }

        if (d_events.size() == 0) {
            d_events.add("TRADE");
        }

        return true;
    }

    private void printErrorInfo(String leadingStr, Element errorInfo)
    throws Exception
    {
        System.out.println(leadingStr + errorInfo.getElementAsString(CATEGORY) +
                           " (" + errorInfo.getElementAsString(MESSAGE) + ")");
    }

    private void printUsage()
    {
        System.out.println("Usage:");
        System.out.println("  Retrieve intraday rawticks ");
        //System.out.println("    [-s     <security	= IBM US Equity>");
        System.out.println("    [-e     <event		= TRADE>");
        System.out.println("    [-sd    <startDateTime  = 2008-02-11T15:30:00>");
        System.out.println("    [-ed    <endDateTime    = 2008-02-11T15:35:00>");
        System.out.println("    [-cc    <includeConditionCodes = false>");
        System.out.println("    [-ip    <ipAddress	= localhost>");
        System.out.println("    [-p     <tcpPort	= 8194>");
        System.out.println("Notes:");
        System.out.println("1) All times are in GMT.");
        System.out.println("2) Only one security can be specified.");
    }
}
