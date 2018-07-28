package edu.buffalo.cse.cse486586.groupmessenger2;


import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.PriorityQueue;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {
    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static final String[] ports = {"11108","11112","11116","11120","11124"};
    static final int SERVER_PORT = 10000;
    public int counter = -1;
    public int proposed = 0;
    public double agreed_val = 0.0;
    public static PriorityQueue<Message1> pQueue = new PriorityQueue<Message1>();
    public static HashMap<String,Double> avd_id= new HashMap<String, Double>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
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
        final EditText editText = (EditText) findViewById(R.id.editText1);
        findViewById(R.id.button4).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = editText.getText().toString().trim() + "\n";
                editText.setText(""); // This is one way to reset the input box.
                TextView tv = (TextView) findViewById(R.id.textView1);
                tv.append("\t" + msg); // This is one way to display a string.
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);
            }
        });
    }

    /*
     * TODO: You need to register and implement an OnClickListener for the "Send" button.
     * In your implementation you need to get the message from the input box (EditText)
     * and send it to other AVDs.
     */
    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {
        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            Uri mUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");
            String p_msg = "";
            while (true)
            {
                try
                {
                    Log.d("Server", "Message receiving");
                    Socket sock = serverSocket.accept();
                    DataInputStream d = new DataInputStream(sock.getInputStream());
                    DataOutputStream dout = new DataOutputStream(sock.getOutputStream());
                    String message = d.readUTF();
                    if(message.length() < 2)
                    {
                        dout.writeUTF("ack");
                        dout.flush();
                        Log.d("Server","Failed Avd"+message);
                        Iterator<Message1> it = pQueue.iterator();
                        while(it.hasNext())
                        {
                            Message1 msg_remove = it.next();
                            String pi = String.valueOf(msg_remove.pval);
                            String[] p=  pi.split("\\.");
                            //String c = new DecimalFormat(".0").format(msg_remove.pval);
                            Log.d("Lost Avd",p[1]);
                            if(p[1].equals(message.trim()))
                            {
                                Log.d("Lost Avd",p[1]);
                                Log.d("Message to be removed :" ,msg_remove.Msg);
                                pQueue.remove(msg_remove);//remove object
                                Log.d("Client","Message removed from queue!");
                            }
                        }

                    }
                    else {
                        String[] output = message.split("\\|");
                        Log.d("output[0]", output[0]);
                        Log.d("output[1]", output[1]);
                        Log.d("output[2]", output[2]);
                        if (output[2].equals("M"))//Send a proposal message to client if its seen for the first time
                        {
                            output[2] = "P";
                            //proposed = proposed + 1;
                            double final_proposed = (double) (proposed) + Double.parseDouble(output[1]);
                            p_msg = output[0] + "|" + final_proposed + "|" + output[2];
                            Log.d("Server", "Proposed message:" + p_msg);
                            Message1 message1 = new Message1(output[0], final_proposed, output[2], false);//put it on queue
                            pQueue.add(message1);
                            if (pQueue.contains(message1)) {
                                Log.d("Server", "Message added to Priority Queue!");
                                Log.d("message:", message1.Msg);
                            }
                            dout.writeUTF(p_msg);//sending an ack to client to close the socket!
                            dout.flush();
                            Log.d("Server", "Proposal Message added to the Queue and sent to the client of avdid:" + output[1]);
                        } else if (output[2].equals("A"))//recieve agreement message from client
                        {
                            dout.writeUTF("ACK");
                            dout.flush();
                            Log.d("Server", "Updating Approval Message in the Queue");
                            Double am_pval = Double.parseDouble(output[1]);
                            int ap = (int) Math.abs(am_pval);
                            proposed = Math.max(proposed, ap) + 1;
                            Iterator<Message1> it = pQueue.iterator();
                            while (it.hasNext()) {
                                Log.d("Server", "Inside iterator.....");
                                Message1 msgs_in_queue = it.next();
                                if (msgs_in_queue.Msg.equals(output[0])) {
                                    Log.d("Server", "found the msg!removing n updating");
                                    pQueue.remove(msgs_in_queue);
                                    String am_msg = output[0];
                                    Message1 am = new Message1(am_msg, am_pval, "A", true);
                                    pQueue.add(am);
                                    if (pQueue.contains(am)) {
                                        Log.d("Server", "Priority Queue Updated");
                                    }
                                }
                            }
                        }
                    }

                    Message1 m2;

                    while (pQueue.peek() != null && pQueue.peek().deliverable == true)
                    {
                        Log.d("Server", "Message Delivery (Writing Key/Value Pair)");
                        m2 = pQueue.poll();
                        if(m2 != null)
                        {
                            String m3_cv = m2.Msg;
                            Log.d("Server", "Message to be written :" + m3_cv);
                            ContentValues cv = new ContentValues();//developer.Android.com
                            publishProgress(m3_cv);
                            counter++;
                            cv.put("key", Integer.toString(counter));
                            cv.put("value", m3_cv);
                            getContentResolver().insert(mUri, cv);//developer.Android.com
                            Log.d("Server", "Message delivered:"+m3_cv);
                        }

                    }
                    sock.close();
                    Log.d("Server", "Message complete");
                }

                catch (SocketTimeoutException e)
                {
                    Log.e(TAG, "ServerTask SocketTimeOutException");
                    e.printStackTrace();
                }
                catch (IOException e)
                {
                    Log.e(TAG, "ServerTask IOException");
                    e.printStackTrace();
                }
                catch (NullPointerException e){
                    Log.e(TAG,"Null Pointer Exception");
                }
                catch (Exception e) {
                    Log.e(TAG, "No Connection");
                    e.printStackTrace();
                }
            }
        }

        protected void onProgressUpdate(String...strings)
        {
            String strReceived = strings[0].trim();
            TextView tv = (TextView) findViewById(R.id.textView1);
            tv.append(strReceived + "\t\n");
            return;
        }
    }



public int handle_failure(int i){
                    Log.e(TAG, "ClientTask SocketTimeOutException");
                    int detect_favd = i;
                    Log.e("CLient:", "Node Failed : avd"+(detect_favd));
                    Log.e("CLient:", "Node removed : avd" + detect_favd);
                    return detect_favd;
}

    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {

            avd_id.put("11108", 0.0);
            avd_id.put("11112", 0.1);
            avd_id.put("11116", 0.2);
            avd_id.put("11120", 0.3);
            avd_id.put("11124", 0.4);
            DataOutputStream bufOut;//Oracle.java
            DataInputStream bufin;//Oracle.java
            boolean fail = false;


            int detect_favd = 5;
            Double pid = avd_id.get(msgs[1]);//Current_id
            Double local_max;
            Socket socket;
            for (int i = 0; i < ports.length; i++) {

                try {
                    if (i != detect_favd) {
                        String remotePort = ports[i];
                        socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(remotePort));
                        socket.setSoTimeout(500);
                        StringBuilder msgToSend = new StringBuilder();
                        msgToSend.append(msgs[0].trim());
                        msgToSend.append("|");
                        msgToSend.append(pid);
                        msgToSend.append("|");
                        msgToSend.append("M");
                        Log.d("CLIENT", "message to send: " + msgToSend.toString());
                        bufOut = new DataOutputStream(socket.getOutputStream());
                        bufOut.writeUTF(msgToSend.toString());
                        bufOut.flush();

                        //Get an Proposed Message from server
                        bufin = new DataInputStream(socket.getInputStream());//Oracle.java
                        String message = bufin.readUTF();//Oracle.java


                        Log.d("Client", "Proposal Message Recieved" + message);

                        String[] pmessage = message.split("\\|");
                        Log.d("Client", "Message" + pmessage[0]);


                        Log.d("Client", "Proposal Value is" + pmessage[1]);
                        Log.d("Client", "Message type (M/P/A):" + pmessage[2]);

                        if (pmessage[2].equals("P")) {
                            Log.d("Client", "Inside P......");
                            local_max = Double.parseDouble(pmessage[1]);
                            Log.d("CLient", "Proposal value is" + local_max);

                            //find the max
                            if (agreed_val < local_max) {
                                agreed_val = local_max;
                            }
                            Log.d("CLient", "Currently the max(A.V) is" + agreed_val);
                            socket.close();
                        }
                    }

                } catch (SocketTimeoutException e) {
                    detect_favd = handle_failure(i);
                    fail = true;
                } catch (UnknownHostException e) {
                    Log.e(TAG, "ClientTask UnknownHostException");
                } catch (IOException e) {
                     detect_favd = handle_failure(i);
                     fail = true;
                }  catch (NullPointerException e){
                     detect_favd = handle_failure(i);
                     fail = true;

                }

            }

                //broadcasting
                if(detect_favd < 5 && fail == true) {
                    for (int i = 0; i < ports.length; i++)
                    {
                        try {
                            if (i != detect_favd) {
                                String remotePort = ports[i];
                                socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                        Integer.parseInt(remotePort));
                                bufOut = new DataOutputStream(socket.getOutputStream());
                                bufOut.writeUTF(String.valueOf(detect_favd));
                                bufOut.flush();
                                Log.d("Client", "Broadcasting avd" + detect_favd + "to Avd"+i);
                                bufin = new DataInputStream(socket.getInputStream());
                                String ack = bufin.readUTF();
                                if (ack.equals("ack")) {
                                    Log.d("Ack!","Ack recieved");
                                    socket.close();
                                }
                            }

                        } catch (Exception e)
                        {
                            Log.e(TAG, "ClientTask2.0 Exception");

                        }

                    }
                    fail = false;
                }

//----------------------sending agreement message-----------------------------------------------------



            //creating agreement message
            String accept_no = String.valueOf(agreed_val);
            String amessage = msgs[0].trim() + "|" + accept_no + "|" + "A";
            Log.d("Client", "Agreement Message:" + amessage);

            for (int j = 0; j < ports.length; j++) {
                try {
                    if(j != detect_favd) {
                        String remotePort = ports[j];
                        socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(remotePort));
                        socket.setSoTimeout(500);
                        bufOut = new DataOutputStream(socket.getOutputStream());
                        bufOut.writeUTF(amessage);
                        bufOut.flush();
                        Log.d("Client", "Agreement Message Sent :" + amessage);
                        bufin = new DataInputStream(socket.getInputStream());
                        if (bufin.readUTF() != null) {
                            socket.close();
                        }
                    }

                }
                catch (SocketTimeoutException e)
                {
                detect_favd = handle_failure(j);
                fail = true;
                }
                catch (UnknownHostException e) {
                    Log.e(TAG, "ClientTask UnknownHostException");

                } catch (IOException e) {
                    detect_favd = handle_failure(j);
                    fail = true;

                }catch (NullPointerException e)
                {
                    detect_favd = handle_failure(j);
                    fail = true;
                }
            }

             if(detect_favd < 5 && fail == true) {
                    for (int i = 0; i < ports.length; i++){
                        try{
                                        if (i != detect_favd) {
                                           String remotePort = ports[i];
                                           socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),Integer.parseInt(remotePort));
                                           bufOut = new DataOutputStream(socket.getOutputStream());
                                           bufOut.writeUTF(String.valueOf(detect_favd));
                                           bufOut.flush();
                                           Log.d("Client", "Broadcasting avd" + detect_favd + "to Avd"+i);
                                           bufin = new DataInputStream(socket.getInputStream());
                                           String ack = bufin.readUTF();
                                           if (ack.equals("ack")) {
                                               Log.d("Ack!","Ack recieved");
                                               socket.close();
                                           }
                                        }

                        }
                        catch (Exception e){
                                        Log.e(TAG, "ClientTask2.0 Exception");
                        }
                    }
                    fail = false;

 }

            return null;


        }

    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }
}
