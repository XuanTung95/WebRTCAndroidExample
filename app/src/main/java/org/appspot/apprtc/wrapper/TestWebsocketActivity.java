package org.appspot.apprtc.wrapper;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;

import org.appspot.apprtc.R;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.CompletableTransformer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import ua.naiksoftware.stomp.Stomp;
import ua.naiksoftware.stomp.StompClient;
import ua.naiksoftware.stomp.dto.StompHeader;

public class TestWebsocketActivity extends Activity {

    private static final String TAG = "MainActivity";

    public static final String ANDROID_EMULATOR_LOCALHOST = "192.168.70.14";
    public static final String SERVER_PORT = "8080";

    private StompClient mStompClient;
    private Disposable mRestPingDisposable;
    private CompositeDisposable compositeDisposable;

    Button sendBtn;
    Button connect;
    Button disconnect;
    TextView url;
    TextView to;
    TextView from;
    TextView input;
    TextView msg;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.test);
        sendBtn = findViewById(R.id.button);
        sendBtn.setOnClickListener(v -> {
            sendEchoViaStomp();
        });
        connect = findViewById(R.id.connect);
        connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectStomp();
            }
        });
        disconnect = findViewById(R.id.disconnect);
        disconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                disconnectStomp();
            }
        });
        url = findViewById(R.id.url);
        from = findViewById(R.id.from);
        to = findViewById(R.id.to);
        input = findViewById(R.id.input);
        msg = findViewById(R.id.msg);
        resetSubscriptions();
    }

    public void disconnectStomp() {
        mStompClient.disconnect();
    }

    public static final String LOGIN = "login";

    public static final String PASSCODE = "passcode";

    public void connectStomp() {

        List<StompHeader> headers = new ArrayList<>();
        headers.add(new StompHeader(LOGIN, "guest"));
        headers.add(new StompHeader(PASSCODE, "passcode"));
        mStompClient = Stomp.over(Stomp.ConnectionProvider.OKHTTP, url.getText().toString());
        mStompClient.withClientHeartbeat(3000).withServerHeartbeat(30000);

        resetSubscriptions();

        Disposable dispLifecycle = mStompClient.lifecycle()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(lifecycleEvent -> {
                    switch (lifecycleEvent.getType()) {
                        case OPENED:
                            toast("Stomp connection opened");
                            break;
                        case ERROR:
                            Log.e(TAG, "Stomp connection error", lifecycleEvent.getException());
                            toast("Stomp connection error");
                            break;
                        case CLOSED:
                            toast("Stomp connection closed");
                            resetSubscriptions();
                            break;
                        case FAILED_SERVER_HEARTBEAT:
                            toast("Stomp failed server heartbeat");
                            break;
                    }
                });


        compositeDisposable.add(dispLifecycle);

        // Receive greetings
        Disposable dispTopic = mStompClient.topic("/user/"+from.getText().toString()+"/queue/messages")
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(topicMessage -> {
                    Log.d(TAG, "Received " + topicMessage.getPayload());
                    msg.setText(topicMessage.getPayload());
                }, throwable -> {
                    Log.e(TAG, "Error on subscribe topic", throwable);
                });

        compositeDisposable.add(dispTopic);

        mStompClient.connect(headers);
    }

    public static class SignalMsg {
        public SignalMsg(String from, String to, String type, String content) {
            this.from = from;
            this.to = to;
            this.type = type;
            this.content = content;
        }

        String from;
        String to;
        String type;
        String content;
    }

    public void sendEchoViaStomp() {
//        if (!mStompClient.isConnected()) return;
        SignalMsg msg = new SignalMsg("111", to.getText().toString(), "type", input.getText().toString());
        compositeDisposable.add(mStompClient.send("/app/signal", new Gson().toJson(msg))
                .compose(applySchedulers())
                .subscribe(() -> {
                    Log.d(TAG, "STOMP echo send successfully");
                }, throwable -> {
                    Log.e(TAG, "Error send STOMP echo", throwable);
                    toast(throwable.getMessage());
                }));
    }

    /*public void sendEchoViaRest(View v) {
        mRestPingDisposable = RestClient.getInstance().getExampleRepository()
                .sendRestEcho("Echo REST " + mTimeFormat.format(new Date()))
                .compose(applySchedulers())
                .subscribe(() -> {
                    Log.d(TAG, "REST echo send successfully");
                }, throwable -> {
                    Log.e(TAG, "Error send REST echo", throwable);
                    toast(throwable.getMessage());
                });
    }

    private void addItem(EchoModel echoModel) {
        mDataSet.add(echoModel.getEcho() + " - " + mTimeFormat.format(new Date()));
        mAdapter.notifyDataSetChanged();
        mRecyclerView.smoothScrollToPosition(mDataSet.size() - 1);
    }*/

    private void toast(String text) {
        Log.i(TAG, text);
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    protected CompletableTransformer applySchedulers() {
        return upstream -> upstream
                .unsubscribeOn(Schedulers.newThread())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    private void resetSubscriptions() {
        if (compositeDisposable != null) {
            compositeDisposable.dispose();
        }
        compositeDisposable = new CompositeDisposable();
    }

    @Override
    protected void onDestroy() {
        mStompClient.disconnect();

        if (mRestPingDisposable != null) mRestPingDisposable.dispose();
        if (compositeDisposable != null) compositeDisposable.dispose();
        super.onDestroy();
    }
}