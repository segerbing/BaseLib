package com.liang530.photopicker;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewStub;
import android.view.animation.LinearInterpolator;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import core.base.R;
import com.liang530.application.BaseActivity;
import com.liang530.log.L;
import com.liang530.photopicker.adapters.FloderAdapter;
import com.liang530.photopicker.adapters.PhotoRcyAdapter;
import com.liang530.photopicker.beans.MediaBean;
import com.liang530.photopicker.beans.MediaFloder;
import com.liang530.photopicker.beans.SelectImageEvent;
import com.liang530.photopicker.beans.SelectStatusEvent;
import com.liang530.photopicker.utils.MediaManager;
import com.liang530.photopicker.utils.OtherUtils;


/**
 * @Class: PhotoPickerActivity
 * @Description: 照片选择界面
 * @author: lling(www.liuling123.com)
 * @Date: 2015/11/4
 */
public class PhotoPickerActivity extends BaseActivity implements PhotoRcyAdapter.PhotoSelectListener{

    public final static String TAG = "PhotoPickerActivity";

    public final static String KEY_RESULT = "picker_result";
    public final static int REQUEST_CAMERA = 1;

    /** 是否显示相机 */
    public final static String EXTRA_SHOW_CAMERA = "is_show_camera";
    /** 照片选择标志 */
    public final static String FLAG = "flag";
    /** 最大选择数量 */
    public final static String EXTRA_MAX_MUN = "max_num";
    /** 单选 */
    public final static int MODE_SINGLE = 0;
    /** 多选 */
    public final static int MODE_MULTI = 1;
    /** 默认最大选择数量 */
    public final static int DEFAULT_NUM = 9;


    /** 是否显示相机，默认不显示 */
    private boolean mIsShowCamera = false;
    /** 照片选择标示*/
    private String flag ;
    /** 最大选择数量，仅多选模式有用 */
    private int mMaxNum;
    private String orderId;
    private RecyclerView recyclerView;
    private List<MediaBean> mMediaBeanLists = new ArrayList<MediaBean>();
    private PhotoRcyAdapter mPhotoAdapter;
    private ProgressDialog mProgressDialog;
    private ListView mFloderListView;

    private TextView previewBtn;
    private TextView mPhotoNameTV;
    private Button mCommitBtn;
    GridLayoutManager manager;
    /** 文件夹列表是否处于显示状态 */
    boolean mIsFloderViewShow = false;
    /** 文件夹列表是否被初始化，确保只被初始化一次 */
    boolean mIsFloderViewInit = false;

    /** 拍照时存储拍照结果的临时文件 */
    private File mTmpFile;
    MediaManager mediaManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_picker);
        EventBus.getDefault().register(this);
        initIntentParams();
        initView();
        if (!OtherUtils.isExternalStorageAvailable()) {
            Toast.makeText(this, "No SD card!", Toast.LENGTH_SHORT).show();
            return;
        }
        getPhotosTask.execute();
    }

    private void initView() {
        recyclerView = (RecyclerView) findViewById(R.id.photo_recyclerview);
        previewBtn = (TextView) findViewById(R.id.btn_preview);
        mCommitBtn = (Button) findViewById(R.id.commit);
        mCommitBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MediaManager.selectOK();
                finish();
            }
        });
        previewBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(MediaManager.getSelectMediaBeans()!=null&&MediaManager.getSelectMediaBeans().size()>0){
                    PhotoPreviewActivity_2.startPreviewSelectPhoto(PhotoPickerActivity.this);
                }else{
                    Toast.makeText(PhotoPickerActivity.this,"请先选择图片！",Toast.LENGTH_LONG).show();
                }
            }
        });
        mPhotoNameTV = (TextView) findViewById(R.id.floder_name);
        ((RelativeLayout) findViewById(R.id.bottom_tab_bar)).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                //消费触摸事件，防止触摸底部tab栏也会选中图片
                return true;
            }
        });
        ((ImageView) findViewById(R.id.btn_back)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        manager=new GridLayoutManager(this,3);
        manager.setOrientation(GridLayoutManager.VERTICAL);
        recyclerView.setLayoutManager(manager);
    }

    /**
     * 初始化选项参数
     */
    private void initIntentParams() {
        mIsShowCamera = getIntent().getBooleanExtra(EXTRA_SHOW_CAMERA, false);
        flag = getIntent().getStringExtra(FLAG);
        mMaxNum = getIntent().getIntExtra(EXTRA_MAX_MUN, DEFAULT_NUM);
        mediaManager=MediaManager.getInstance();
        mediaManager.init(getApplicationContext(),flag);
    }

    private void getPhotosSuccess() {
        mProgressDialog.dismiss();
        mMediaBeanLists.addAll(mediaManager.getMediaFloder(MediaManager.ALL_PHOTO).getMediaBeanList());

        mPhotoAdapter = new PhotoRcyAdapter(this,MediaManager.ALL_PHOTO, mMediaBeanLists,this);
        mPhotoAdapter.setIsShowCamera(mIsShowCamera);
        mPhotoAdapter.setMaxNum(mMaxNum);
        recyclerView.setAdapter(mPhotoAdapter);
        Set<String> keys = mediaManager.getMediaMap().keySet();
        final List<MediaFloder> floders = new ArrayList<MediaFloder>();
        for (String key : keys) {
            if (MediaManager.ALL_PHOTO.equals(key)) {
                MediaFloder floder = mediaManager.getMediaMap().get(key);
                floder.setIsSelected(true);
                floders.add(0, floder);
            }else {
                floders.add(mediaManager.getMediaMap().get(key));
            }
        }
        mPhotoNameTV.setOnClickListener(new View.OnClickListener() {
            @TargetApi(Build.VERSION_CODES.KITKAT)
            @Override
            public void onClick(View v) {
                toggleFloderList(floders);
            }
        });
    }

    /**
     * 显示或者隐藏文件夹列表
     * @param floders
     */
    private void toggleFloderList(final List<MediaFloder> floders) {
        //初始化文件夹列表
        if(!mIsFloderViewInit) {
            ViewStub floderStub = (ViewStub) findViewById(R.id.floder_stub);
            floderStub.inflate();
            View dimLayout = findViewById(R.id.dim_layout);
            mFloderListView = (ListView) findViewById(R.id.listview_floder);
            final FloderAdapter adapter = new FloderAdapter(this, floders);
            mFloderListView.setAdapter(adapter);
            mFloderListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    for (MediaFloder floder : floders) {
                        floder.setIsSelected(false);
                    }
                    MediaFloder floder = floders.get(position);
                    floder.setIsSelected(true);
                    adapter.notifyDataSetChanged();

                    mMediaBeanLists.clear();
                    mMediaBeanLists.addAll(floder.getMediaBeanList());
                    if (MediaManager.ALL_PHOTO.equals(floder.getName())) {
                        mPhotoAdapter.setIsShowCamera(mIsShowCamera);
                    } else {
                        mPhotoAdapter.setIsShowCamera(false);
                    }
                    //这里重新设置adapter而不是直接notifyDataSetChanged，是让GridView返回顶部
                    recyclerView.setAdapter(mPhotoAdapter);
                    previewBtn.setText(OtherUtils.formatResourceString(getApplicationContext(),
                            R.string.photos_num, mMediaBeanLists.size()));
                    mPhotoNameTV.setText(floder.getName());
                    toggle();
                }
            });
            dimLayout.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (mIsFloderViewShow) {
                        toggle();
                        return true;
                    } else {
                        return false;
                    }
                }
            });
            initAnimation(dimLayout);
            mIsFloderViewInit = true;
        }
        toggle();
    }

    /**
     * 弹出或者收起文件夹列表
     */
    private void toggle() {
        if(mIsFloderViewShow) {
            outAnimatorSet.start();
            mIsFloderViewShow = false;
        } else {
            inAnimatorSet.start();
            mIsFloderViewShow = true;
        }
    }


    /**
     * 初始化文件夹列表的显示隐藏动画
     */
    AnimatorSet inAnimatorSet = new AnimatorSet();
    AnimatorSet outAnimatorSet = new AnimatorSet();
    private void initAnimation(View dimLayout) {
        ObjectAnimator alphaInAnimator, alphaOutAnimator, transInAnimator, transOutAnimator;
        //获取actionBar的高
        TypedValue tv = new TypedValue();
        int actionBarHeight = 0;
        if (getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
            actionBarHeight = TypedValue.complexToDimensionPixelSize(tv.data, getResources().getDisplayMetrics());
        }
        /**
         * 这里的高度是，屏幕高度减去上、下tab栏，并且上面留有一个tab栏的高度
         * 所以这里减去3个actionBarHeight的高度
         */
        int height = OtherUtils.getHeightInPx(this) - 3*actionBarHeight;
        alphaInAnimator = ObjectAnimator.ofFloat(dimLayout, "alpha", 0f, 0.7f);
        alphaOutAnimator = ObjectAnimator.ofFloat(dimLayout, "alpha", 0.7f, 0f);
        transInAnimator = ObjectAnimator.ofFloat(mFloderListView, "translationY", height , 0);
        transOutAnimator = ObjectAnimator.ofFloat(mFloderListView, "translationY", 0, height);

        LinearInterpolator linearInterpolator = new LinearInterpolator();

        inAnimatorSet.play(transInAnimator).with(alphaInAnimator);
        inAnimatorSet.setDuration(300);
        inAnimatorSet.setInterpolator(linearInterpolator);
        outAnimatorSet.play(transOutAnimator).with(alphaOutAnimator);
        outAnimatorSet.setDuration(300);
        outAnimatorSet.setInterpolator(linearInterpolator);
    }

    /**
     * 选择文件夹
     * @param mediaFloder
     */
    public void selectFloder(MediaFloder mediaFloder) {
        mPhotoAdapter.setDatas(mediaFloder.getMediaBeanList());
        mPhotoAdapter.notifyDataSetChanged();
    }

    /**
     * 获取照片的异步任务
     */
    private AsyncTask getPhotosTask = new AsyncTask() {
        @Override
        protected void onPreExecute() {
            mProgressDialog = ProgressDialog.show(PhotoPickerActivity.this, null, "loading...");
        }

        @Override
        protected Object doInBackground(Object[] params) {
            mediaManager.initPhotos(getApplicationContext());
            return null;
        }

        @Override
        protected void onPostExecute(Object o) {
            getPhotosSuccess();
        }
    };


    /**
     * 接收图片预览提交选择ok的请求，关闭本页面
     * @param event
     */
    @Subscribe(sticky = true,threadMode = ThreadMode.MAIN)
    public void onEventMainThread(SelectImageEvent event){
        finish();
    }
    /**
     * 接受相册预览的选中改动
     * @param event
     */
    public void onEventMainThread(SelectStatusEvent event){
        L.e("hongliang", "接收相册列表更新：" + event);
        int firstVisibleItemPosition = manager.findFirstVisibleItemPosition();
        int lastVisibleItemPosition = manager.findLastVisibleItemPosition();
        Integer realFirstPosition = mPhotoAdapter.getRealPosition(firstVisibleItemPosition);
        Integer realLastPosition = mPhotoAdapter.getRealPosition(lastVisibleItemPosition);
        if(realFirstPosition==null){//起始点是null，说明有个照相机在
            realFirstPosition=0;
        }
        L.e("hongliang", "realFirstPosition:" + realFirstPosition + "  realLastPosition=" + realLastPosition);
        if(realFirstPosition!=null&&realLastPosition!=null&&realFirstPosition>=0&&realLastPosition>=0&&mPhotoAdapter.getDatas()!=null){
            for(int index=realFirstPosition;index<=realLastPosition;index++){
                L.e("hongliang", "查找中：" + mPhotoAdapter.getDatas().get(index).getId());
                if(mPhotoAdapter.getDatas().get(index).getId()==event.imageId){//找到了更新
                    //有照相机的要加上照相机1个
                    mPhotoAdapter.notifyItemChanged(mPhotoAdapter.isShowCamera()?index+1:index);
                    photoSelectChange(index,event.imageId,event.isSelect);
                }
            }
        }
    }
    //勾选状态变动通知
    @Override
    public void photoSelectChange(int index, int ImageId,boolean isSelect) {
        if(MediaManager.getSelectMediaBeans()!=null&&MediaManager.getSelectMediaBeans().size()>0){
            previewBtn.setVisibility(View.VISIBLE);
            mCommitBtn.setVisibility(View.VISIBLE);
            previewBtn.setText("预览(" + MediaManager.getSelectMediaBeans().size() + ")");
            mCommitBtn.setText("确定("+MediaManager.getSelectMediaBeans().size()+"/"+mMaxNum+")");
        }else{
            previewBtn.setVisibility(View.GONE);
            mCommitBtn.setVisibility(View.GONE);
        }

    }
    //点击拍照的回调
    @Override
    public void gotoCamera() {
//        Toast.makeText(this,"去拍照",Toast.LENGTH_LONG).show();
        // 跳转到系统照相机
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if(cameraIntent.resolveActivity(getPackageManager()) != null){
            // 设置系统相机拍照后的输出路径
            // 创建临时文件
            mTmpFile = OtherUtils.createFile(getApplicationContext());
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(mTmpFile));
            startActivityForResult(cameraIntent, REQUEST_CAMERA);
        }else{
            Toast.makeText(getApplicationContext(),
                    R.string.msg_no_camera, Toast.LENGTH_SHORT).show();
        }
    }
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // 相机拍照完成后，返回图片路径
        if(requestCode == REQUEST_CAMERA){
            if(resultCode == Activity.RESULT_OK) {
                if (mTmpFile != null) {
                    PhotoPreviewActivity_2.startFromCamera(this,mTmpFile.getAbsolutePath());
                }
                L.e(TAG,mTmpFile.getAbsolutePath());
            }else{
                if(mTmpFile != null && mTmpFile.exists()){
                    mTmpFile.delete();
                }
            }
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }
    public static void start(Context context,boolean isShowCamera,String flag,int num) {
        Intent starter = new Intent(context, PhotoPickerActivity.class);
        starter.putExtra(PhotoPickerActivity.EXTRA_SHOW_CAMERA, true);
        starter.putExtra(FLAG, flag);
        starter.putExtra(PhotoPickerActivity.EXTRA_MAX_MUN, num);
        context.startActivity(starter);
    }
}
