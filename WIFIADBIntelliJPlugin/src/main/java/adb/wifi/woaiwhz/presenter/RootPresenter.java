package adb.wifi.woaiwhz.presenter;

import adb.wifi.woaiwhz.base.*;
import adb.wifi.woaiwhz.dispatch.Executor;
import adb.wifi.woaiwhz.dispatch.Handler;
import adb.wifi.woaiwhz.dispatch.Message;
import adb.wifi.woaiwhz.parser.AllDevicesCommand;
import adb.wifi.woaiwhz.parser.ConnectDevice;
import adb.wifi.woaiwhz.parser.ICommand;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by huazhou.whz on 2016/10/14.
 */
public class RootPresenter {
    private final RootView mViewLayer;
    private final Handler mHandler;

    private Project mProject;
    private String mAdbPath;
    private boolean mRunning;

    public RootPresenter(@NotNull RootView view){
        mViewLayer = view;
        mHandler = new CustomHandler();
        mRunning = false;
    }

    public void init(@NotNull Project project){
        mProject = project;
        mAdbPath = Utils.getAdbPath(mProject);

        if(isAdbEmpty()){
            mViewLayer.onADBEmpty();
        }else {
            mViewLayer.onADBComplete(mAdbPath);
        }
    }

    private boolean isAdbEmpty(){
        return Utils.isBlank(mAdbPath);
    }

    public void addDevice(final String deviceId){
        if(Utils.isBlank(deviceId)){
            return;
        }

        lock();

        runOnPooledThread(new Runnable() {
            @Override
            public void run() {
                mHandler.sendMessage(CustomHandler.CHANGE_PROGRESS_TIP,"Connect to " + deviceId);

                final ICommand<String,Boolean> command = new ConnectDevice();
                final String commandString = Utils.concat(mAdbPath,Config.SPACE,command.getCommand(),Config.SPACE,deviceId);
                final String result = CommandExecute.execute(commandString);
                final boolean connected = command.parse(result);

                final Device device = new Device(deviceId,connected,null,null,null);

                final Message message = new Message(CustomHandler.CONNECT_DEVICE,device);
                mHandler.sendMessage(message);

                realGetAllDevices();
            }
        });
    }

    public void getAllDevices(){
        if(isAdbEmpty() && !isRunning()){
            return;
        }

        lock();

        runOnPooledThread(new Runnable() {
            @Override
            public void run() {
                realGetAllDevices();
            }
        });
    }

    private void realGetAllDevices(){
        mHandler.sendMessage(CustomHandler.CHANGE_PROGRESS_TIP,"Refresh devices list");

        final ICommand<String,Device[]> parser = new AllDevicesCommand();
        final String command = Utils.concat(mAdbPath, Config.SPACE,parser.getCommand());
        final String result = CommandExecute.execute(command);
        final Device[] devices = parser.parse(result);

        final Message message = new Message(CustomHandler.GET_ALL_DEVICES,devices);
        mHandler.sendMessage(message);
    }

    private void mockWait(){
        try{
            Thread.sleep(2000);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void runOnPooledThread(@NotNull Runnable runnable){
        Executor.execute(runnable);
    }

    private boolean isRunning(){
        return mRunning;
    }

    private void lock(){
        if(!mRunning) {
            mRunning = true;
            mViewLayer.showLoading();
        }
    }

    private void unlock(){
        if(mRunning) {
            mRunning = false;
            mViewLayer.hideLoading();
        }
    }

    private class CustomHandler extends Handler {
        private static final int GET_ALL_DEVICES = 1;
        private static final int CONNECT_DEVICE = 1 << 1;
        private static final int CHANGE_PROGRESS_TIP = 1 << 2;

        @Override
        protected void handleMessage(@NotNull Message msg) {
            final int what = msg.what;

            switch (what) {
                case GET_ALL_DEVICES:
                    handleAllDevicesAction(msg);
                    unlock();
                    break;

                case CONNECT_DEVICE:
                    handleConnectDeviceAction(msg);
                    break;

                case CHANGE_PROGRESS_TIP:
                    handleChangeProgressTipAction(msg);
                    break;

                default:
                    break;
            }
        }

        private void handleChangeProgressTipAction(@NotNull Message msg){
            final String newTip = msg.get();

            if(Utils.isBlank(newTip)){
                mViewLayer.refreshProgressTip(Config.DEFAULT_PROGRESS_TIP);
            }else {
                mViewLayer.refreshProgressTip(newTip);
            }
        }

        private void handleConnectDeviceAction(@NotNull Message msg){
            final Device device = msg.get();

            if(device == null){
                return;
            }

            if(device.state){
                Notify.alert(Utils.concat("connect to",Config.SPACE,device.id));
            }else {
                Notify.alert(Utils.concat("cannot connect to",Config.SPACE,device.id));
            }
        }

        private void handleAllDevicesAction(@NotNull Message msg){
            final Device[] devices = msg.get();

            if (devices == null){
                mViewLayer.refreshDevices(null);
                return;
            }

            mViewLayer.refreshDevices(devices);
        }

    }

    public interface RootView{
        void onADBEmpty();
        void onADBComplete(String path);
        void showLoading();
        void hideLoading();
        void refreshDevices(@Nullable Device[] devices);
        void refreshProgressTip(@NotNull String tip);
    }
}