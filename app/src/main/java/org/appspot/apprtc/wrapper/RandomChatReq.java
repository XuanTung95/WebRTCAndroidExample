package org.appspot.apprtc.wrapper;

public class RandomChatReq {
    public String signature;
    public String from;
    public String room;
    public String talkAbout;
    public Integer birthFrom;
    public Integer birthTo;
    public long reqTimeMillis;

    @Override
    public String toString() {
        return "RandomChatReq{" +
                "signature='" + signature + '\'' +
                ", from='" + from + '\'' +
                ", room='" + room + '\'' +
                ", talkAbout='" + talkAbout + '\'' +
                ", birthFrom=" + birthFrom +
                ", birthTo=" + birthTo +
                '}';
    }
}