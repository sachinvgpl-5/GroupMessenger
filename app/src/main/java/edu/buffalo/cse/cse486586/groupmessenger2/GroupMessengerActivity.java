package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StreamCorruptedException;
import java.lang.reflect.Array;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * 
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {

    static final String TAG = GroupMessengerActivity.class.getSimpleName();

    static  String[] REMOTE_PORT_LIST = {"11108", "11112", "11116", "11120", "11124"};

    static List<String> REMOTE_PORT = new CopyOnWriteArrayList<String>(REMOTE_PORT_LIST);



    static String remove_port;

    static final int SERVER_PORT = 10000;

    private static final String KEY_FIELD = "key";

    private static final String VALUE_FIELD = "value";

    private static Map<String, Socket> socket = new HashMap<String, Socket>();

    private PriorityQueue<QueueElement> holdback_queue= new PriorityQueue<QueueElement>();

    static int message_seqno = -1;
    static int seqno = -1;
    static int prev_accepted_seqno = -1;

    private AtomicInteger count = new AtomicInteger(0);

    private static AtomicIntegerArray received_props = new AtomicIntegerArray(2);

    private final Uri mUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");
    /**
     * buildUri() demonstrates how to build a URI for a ContentProvider.
     *
     * @param scheme
     * @param authority
     * @return the URI
     */
    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_group_messenger);

        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            /*
             * Log is a good way to debug your code. LogCat prints out all the messages that
             * Log class writes.
             *
             * Please read http://developer.android.com/tools/debugging/debugging-projects.html
             * and http://developer.android.com/tools/debugging/debugging-log.html
             * for more information on debugging.
             */
            Log.e(TAG, "Can't create a ServerSocket");
            Log.getStackTraceString(e);
            return;
        }



        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        
        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));
        
        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */
        final EditText editText = (EditText) findViewById(R.id.editText1);

        Log.v("Beginning", myPort );

        findViewById(R.id.button4).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String msg = editText.getText().toString() + "\n";
                        editText.setText(""); // This is one way to reset the input box.

                        received_props.set(0, -1);
                        received_props.set(1, -1);
                        count.set(0);

                        remove_port = "";

                        message_seqno += 1;

                        for (String remote_port : REMOTE_PORT) {
                            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, String.valueOf(message_seqno), myPort, remote_port);
                        }


                        while(true) {
                            {
                                if(count.get() >= 4)
                                    break;
                            }

                        }

                        for (String remote_port : REMOTE_PORT) {
                                new ClientBroadcast().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, String.valueOf(message_seqno), String.valueOf(received_props.get(0)), String.valueOf(received_props.get(1)), remote_port);
                        }


                    }

                }
        );
    }


    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];

            Socket clientSocket = null;
            BufferedReader in;
            PrintWriter out;
            int origin;
            QueueElement element = null;
            while(true) {
                String inputString;
                try {
                    clientSocket = serverSocket.accept();
                    in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    out = new PrintWriter(clientSocket.getOutputStream(), true);
                    if ((inputString = in.readLine()) != null)
                    {
                        String[] split_input = inputString.split(",");

                        Log.v("Received message", inputString);
                        String message = split_input[0];
                        String msg_id = split_input[1];
                        int proposer = Integer.parseInt(split_input[2]);


                        if (seqno < prev_accepted_seqno) {
                            seqno = prev_accepted_seqno;
                        }
                        seqno += 1;
                        element = new QueueElement(msg_id, seqno, proposer, message, false);
                        holdback_queue.add(element);
                        Log.v("server", "Initially saved element: "+element.toString());
                        out.println(msg_id + "," + seqno);
                        Log.v("sending proposal from S", msg_id + "," + seqno);

                        inputString = in.readLine();
                        split_input = inputString.split(",");
                        Log.v("received broadcast", inputString);
                        int seqno = Integer.parseInt(split_input[2]);
                        origin = Integer.parseInt(split_input[3]);
                        QueueElement deliver_element;

                        if (holdback_queue.contains(element)) {
                            Log.v("Stored Element", "broadcast" + element.toString());
                            holdback_queue.remove(element);
                            element.setSeqno(seqno);
                            element.setOrigin(origin);
                            element.MakeDeliverable();
                            holdback_queue.add(element);
                            prev_accepted_seqno = seqno;
                        }

                        while (!holdback_queue.isEmpty() && holdback_queue.peek().isDeliverable()) {
                                Log.v("HoldbackQueue", "Reaches here!!!");
                                deliver_element = holdback_queue.poll();
                                publishProgress(deliver_element.getMsg(), String.valueOf(deliver_element.getSeqno()));
                        }


                    }

                    if (clientSocket.isClosed()) {
                        Log.v("server status", "exiting");
                        break;
                    }

                }catch(NullPointerException e) {
                    Log.v(TAG, "Target socket failed");
                    holdback_queue.remove(element);
                    seqno -= 1;

                    try {
                        clientSocket.close();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
                catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");
                }
                catch (IOException e) {
                    Log.v (TAG, "IO Exception");
                Log.e(TAG, e.toString());
                }


            }
            return null;
        }

        protected void onProgressUpdate(String...strings) {
            /*
             * The following code displays what is received in doInBackground().
             */

            ContentValues cv = new ContentValues();
            String strReceived = strings[0].trim();
            TextView remoteTextView = (TextView) findViewById(R.id.textView1);

            /*
             * The following code creates a file in the AVD's internal storage and stores a file.
             *
             * For more information on file I/O on Android, please take a look at
             * http://developer.android.com/training/basics/data-storage/files.html
             */
            cv.put(KEY_FIELD, strings[1]);
            cv.put(VALUE_FIELD, strings[0]);
            try {
                getContentResolver().insert(mUri, cv);
            } catch (Exception ex) {
                Log.e(TAG, ex.toString());
            }

            remoteTextView.append(strReceived + ":"+strings[1]+ "\t\n");

            return;
        }
    }

    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {

            String msg = msgs[0].trim();

            int msg_id = Integer.parseInt(msgs[1]);

            String myport = msgs[2];

            String remotePort = msgs[3];

            String msg_to_send =  msg + "," + msg_id + "," + myport;

            String proposed_order;

            PrintWriter out = null;

            BufferedReader in = null;

            try {


                socket.put(remotePort, new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(remotePort)));

                /* Client Code that sends the received messages to the server as long
                 * as the socket connection is alive*/
                out = new PrintWriter(socket.get(remotePort).getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.get(remotePort).getInputStream()));
                out.println(msg_to_send);
                Log.v("sending message from", myport);


                if((proposed_order = in.readLine()) != null)
                {
                    Log.v("client: Proposed order", proposed_order + ", From: " + remotePort);
                    String[] proposed_values = proposed_order.split(",");
                    String msgid = proposed_values[0];
                    int proposed_seq = Integer.parseInt(proposed_values[1]);
                    synchronized (this)
                    {

                        if (received_props.get(0) < proposed_seq)
                        {
                            received_props.set(0, proposed_seq);
                            received_props.set(1, Integer.parseInt(remotePort));
                        }
                        else if (received_props.get(0) == proposed_seq && received_props.get(1) > Integer.parseInt(remotePort))
                            received_props.set(1, Integer.parseInt(remotePort));

                        count.incrementAndGet();
                    }
                }


            } catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");

            }
            catch (StreamCorruptedException e) {
                REMOTE_PORT.remove(remotePort);
                remove_port = remotePort;
            }
            catch (ConnectException e) {
                REMOTE_PORT.remove(remotePort);
                remove_port = remotePort;
            }
            catch (SocketTimeoutException e) {
                REMOTE_PORT.remove(remotePort);
                remove_port = remotePort;

            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }

            return null;
        }

    }

    private class ClientBroadcast extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {


            String msg_id = msgs[0];

            String seqno = msgs[1];

            String proposed_by = msgs[2];

            String remotePort = msgs[3];

            String msg_to_broadcast = "broadcast" + "," + msg_id + "," + seqno + "," + proposed_by;
            Log.v("sending broadcast to ", remotePort);

            PrintWriter out =null;

            try {
                //
                /* Client Code that sends the received messages to the server as long
                 * as the socket connection is alive*/
                out = new PrintWriter(socket.get(remotePort).getOutputStream(), true);
                out.println(msg_to_broadcast);

            } catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");

            }
            catch (StreamCorruptedException e) {
                REMOTE_PORT.remove(remotePort);
                remove_port = remotePort;
            }
            catch (ConnectException e) {
                Log.e("Client", "Connection exception");
                remove_port = remotePort;
                REMOTE_PORT.remove(remotePort);

            }
            catch (SocketTimeoutException e) {
                Log.e("Client", "Socket Timed Out");
                remove_port = remotePort;
                REMOTE_PORT.remove(remotePort);

            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
            return null;
        }
    }



    private class QueueElement implements Comparable<QueueElement> {

        private String msgid;
        private String msg;
        private int origin;
        private int seqno;
        private Boolean deliverable;


        public  QueueElement(String msgid, int seqno, int origin, String msg, Boolean deliverable)
        {
            this.msgid = msgid;
            this.seqno = seqno;
            this.origin = origin;
            this.msg = msg;
            this.deliverable = deliverable;
        }

        public int getSeqno() {
            return this.seqno;
        }

        public int getOrigin() {

            return this.origin;
        }

        public String getMsg() {

            return this.msg;
        }

        public void setSeqno(int seqno) {

            this.seqno = seqno;
        }

        public void setOrigin(int origin) {
            this.origin = origin;
        }

        public void MakeDeliverable() {

            this.deliverable = true;
        }

        public Boolean isDeliverable() {

            return this.deliverable;
        }

        @Override
        public String toString() {
            return  "origin:"+this.getOrigin() + ",\n" + "sequence no:"+this.getSeqno() + ",\n" + "messasge: "+ this.getMsg() + ",\nis Deliverable:  " + this.isDeliverable().toString();
        }

        @Override
        public int compareTo(QueueElement another) {
            if (this.getSeqno() > another.getSeqno())
            {
                return 1;
            }
            else if(this.getSeqno() < another.getSeqno())
            {
                return -1;
            }
            else{
                if(this.getOrigin() > another.getOrigin())
                    return 1;
                else if(this.getOrigin() < another.getOrigin())
                    return -1;
                return 0;
            }
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

}
