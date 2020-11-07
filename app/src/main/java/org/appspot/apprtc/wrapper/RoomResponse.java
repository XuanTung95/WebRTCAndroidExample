package org.appspot.apprtc.wrapper;

public class RoomResponse extends BaseResponse {
    public String roomId;
    public Boolean isInitiator;
    public String signature;
    public PublicInfo partnerInfo;
    public String partnerSID;

    public RoomResponse() {
        this.type = BaseResponse.TYPE_ROOM_CHAT;
        this.status = BaseResponse.SUCCESS;
    }

    public RoomResponse leaveRoom(){
        this.type = BaseResponse.TYPE_LEAVE_ROOM;
        this.status = BaseResponse.SUCCESS;
        this.code = Code.LEAVE_ROOM;
        return this;
    }

}