package io.github.lgp547.anydoorplugin.request;

public class RequestHistoryItem {
    private String name; private String target; private String status; private long time; private String requestText;
    public RequestHistoryItem() { }
    public RequestHistoryItem(String name, String target, String status, long time) { this.name=name;this.target=target;this.status=status;this.time=time; }
    public String getName(){return name;} public void setName(String name){this.name=name;}
    public String getTarget(){return target;} public void setTarget(String target){this.target=target;}
    public String getStatus(){return status;} public void setStatus(String status){this.status=status;}
    public long getTime(){return time;} public void setTime(long time){this.time=time;}
    public String getRequestText(){return requestText;} public void setRequestText(String requestText){this.requestText=requestText;}
}
