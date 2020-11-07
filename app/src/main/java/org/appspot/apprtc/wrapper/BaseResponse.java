package org.appspot.apprtc.wrapper;

public class BaseResponse {
    public static final int SUCCESS = 0;
    public static final int FAILED = 1;
    //
    public static final int TYPE_REGISTER_USR = 1;
    public static final int TYPE_ROOM_CHAT = 2;
    public static final int TYPE_SIGNAL_MSG = 3;
    public static final int TYPE_CREATE_USER = 4;
    public static final int TYPE_LEAVE_ROOM = 5;
    /*public static final int TYPE_4 = 4;
    public static final int TYPE_5 = 5;*/

    public Integer status;
    public String code;
    public Integer type;

    public BaseResponse(Integer status, String msg) {
        this.status = status;
        this.code = msg;
    }

    public BaseResponse(String msg) {
        this.status = SUCCESS;
        this.code = msg;
    }

    public BaseResponse() {
        this.status = SUCCESS;
    }

    public boolean isSuccess(){
        return status != null && status == SUCCESS;
    }
}
