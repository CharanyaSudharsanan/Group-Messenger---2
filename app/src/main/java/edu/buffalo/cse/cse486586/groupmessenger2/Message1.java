package edu.buffalo.cse.cse486586.groupmessenger2;

public class Message1 implements Comparable<Message1>{

        public String Msg;
        public double pval;
        public String Messagetype;
        public boolean deliverable;

        public Message1(String Msg,double pval,String Messagetype,boolean deliverable)
        {
            this.Msg = Msg;
            this.pval = pval;
            this.Messagetype = Messagetype;
            this.deliverable = deliverable;

        }

        @Override
        public int compareTo(Message1 message1)
        {
            if(this.pval > message1.pval)
                return 1;
            else if(this.pval < message1.pval)
                return -1;
            else
                return 0;
        }

}

