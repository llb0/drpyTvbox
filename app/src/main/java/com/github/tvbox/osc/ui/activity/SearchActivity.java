package com.github.tvbox.osc.ui.activity;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.catvod.crawler.JsLoader;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.cache.SearchHistory;
import com.github.tvbox.osc.data.SearchPresenter;
import com.github.tvbox.osc.event.InputMsgEvent;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.event.ServerEvent;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.adapter.PinyinAdapter;
import com.github.tvbox.osc.ui.adapter.SearchAdapter;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.dialog.RemoteDialog;
import com.github.tvbox.osc.ui.dialog.SearchCheckboxDialog;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.ui.tv.QRCodeGen;
import com.github.tvbox.osc.ui.tv.widget.CustomEditText;
import com.github.tvbox.osc.ui.tv.widget.SearchKeyboard;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.SearchHelper;
import com.github.tvbox.osc.util.SettingsUtil;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.jieba_android.JiebaSegmenter;
import com.jieba_android.RequestCallback;
import com.undcover.freedom.pyramid.PythonLoader;
import com.yang.flowlayoutlibrary.FlowLayout;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
public class SearchActivity extends BaseActivity {
    private LinearLayout llLayout;
    private TvRecyclerView mGridView;
    private TvRecyclerView mGridViewWord;
    private SourceViewModel sourceViewModel;
    private CustomEditText etSearch;
    private TextView tvSearch;
    private TextView tvClear;
    private SearchKeyboard keyboard;
    private TextView tvAddress;
    private ImageView ivQRCode;
    private SearchAdapter searchAdapter;
    private PinyinAdapter wordAdapter;
    private String searchTitle = "";
    private ImageView tvSearchCheckboxBtn;
    private TextView filterBtn;

    private RelativeLayout searchTips;
    private FlowLayout tv_history;

    private TextView clearHistory;
    private SearchPresenter searchPresenter;

    private List<String> fenci;
    private String sKey;
    public String keyword;

    private HashMap<String, String> mCheckSources = null;
    private SearchCheckboxDialog mSearchCheckboxDialog = null;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_search;
    }


    private static Boolean hasKeyBoard;

    @Override
    protected void init() {
        disableKeyboard(SearchActivity.this);
        initView();
        initViewModel();
        initData();
    }

    /*
     * 禁止软键盘
     * @param activity Activity
     */
    public static void disableKeyboard(Activity activity) {
        hasKeyBoard = false;
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
    }

    /*
     * 启用软键盘
     * @param activity Activity
     */
    public static void enableKeyboard(Activity activity) {
        hasKeyBoard = true;
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
    }

    public void openSystemKeyBoard() {
        InputMethodManager imm = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(this.getCurrentFocus(), InputMethodManager.SHOW_FORCED);
    }

    public void hideSystemKeyBoard() {
        InputMethodManager imm = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
        if(imm.isActive()){
            imm.hideSoftInputFromWindow(etSearch.getApplicationWindowToken(), InputMethodManager.RESULT_UNCHANGED_SHOWN);
        }
    }

    private List<Runnable> pauseRunnable = null;

    @Override
    protected void onResume() {
        super.onResume();
        if (pauseRunnable != null && pauseRunnable.size() > 0) {
            allRunCount.set(pauseRunnable.size());
            if (sourceViewModel != null) {
                sourceViewModel.initExecutor();
                for (Runnable runnable : pauseRunnable) {
                    sourceViewModel.execute(runnable);
                }
            }
            pauseRunnable.clear();
            pauseRunnable = null;
        }
    }

    private void initView() {
        EventBus.getDefault().register(this);

        llLayout = findViewById(R.id.llLayout);
        etSearch = findViewById(R.id.etSearch);
        tvSearch = findViewById(R.id.tvSearch);
        tvSearchCheckboxBtn = findViewById(R.id.tvSearchCheckbox);
        filterBtn = findViewById(R.id.filterBtn);

        searchTips = findViewById(R.id.search_tips);
        tv_history = findViewById(R.id.tv_history);
        clearHistory = findViewById(R.id.clear_history);

        tvClear = findViewById(R.id.tvClear);
        tvAddress = findViewById(R.id.tvAddress);
        ivQRCode = findViewById(R.id.ivQRCode);
        mGridView = findViewById(R.id.mGridView);

        keyboard = findViewById(R.id.keyBoardRoot);
        mGridViewWord = findViewById(R.id.mGridViewWord);
        mGridViewWord.setHasFixedSize(true);
        mGridViewWord.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        wordAdapter = new PinyinAdapter();
        mGridViewWord.setAdapter(wordAdapter);
        wordAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                keyword = wordAdapter.getItem(position);
                etSearch.setText(keyword);
                search(keyword);
            }
        });
        mGridView.setHasFixedSize(true);

        // lite
        if (Hawk.get(HawkConfig.SEARCH_VIEW, 0) == 0)
            mGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
            // with preview
        else
            mGridView.setLayoutManager(new V7GridLayoutManager(this.mContext, 3));
        searchAdapter = new SearchAdapter();
        mGridView.setAdapter(searchAdapter);
        searchAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                Movie.Video video = searchAdapter.getData().get(position);
                if (video != null) {
                    try {
                        if (sourceViewModel != null) {
                            pauseRunnable = sourceViewModel.shutdownNow();
                            JsLoader.stopAll();
                            sourceViewModel.destroyExecutor();
                        }
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                    Bundle bundle = new Bundle();
                    bundle.putString("id", video.id);
                    bundle.putString("sourceKey", video.sourceKey);
                    jumpActivity(DetailActivity.class, bundle);
                }
            }
        });

        tvSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                if (!TextUtils.isEmpty(keyword)) {
                    search(keyword);
                } else {
                    Toast.makeText(mContext, "输入内容不能为空", Toast.LENGTH_SHORT).show();
                }
            }
        });
        tvClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                etSearch.setText("");
                cancel();
            }
        });
        this.etSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            public void afterTextChanged(Editable s) {
                keyword = s.toString().trim();
                if (TextUtils.isEmpty(keyword)) {
                    cancel();
                    showSuccess();
                    tv_history.setVisibility(View.VISIBLE);
                    searchTips.setVisibility(View.VISIBLE);
                    mGridView.setVisibility(View.GONE);
                }
            }
        });
        etSearch.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if(keyCode == KeyEvent.KEYCODE_ENTER){
                    hideSystemKeyBoard();
                }
                return false;
            }
        });
        etSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                Toast.makeText(mContext,"点击",Toast.LENGTH_SHORT).show();
                if (!hasKeyBoard) enableKeyboard(SearchActivity.this);
                openSystemKeyBoard();//再次尝试拉起键盘
                SearchActivity.this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
        });

        clearHistory.setOnClickListener(v -> {
            searchPresenter.clearSearchHistory();
            initSearchHistory();
        });
//        etSearch.setOnFocusChangeListener(tvSearchFocusChangeListener);
        keyboard.setOnSearchKeyListener(new SearchKeyboard.OnSearchKeyListener() {
            @Override
            public void onSearchKey(int pos, String key) {
                if (pos > 1) {
                    String text = etSearch.getText().toString().trim();
                    text += key;
                    etSearch.setText(text);
                    if (text.length() > 0) {
                        loadRec(text);
                    }
                } else if (pos == 1) {
                    String text = etSearch.getText().toString().trim();
                    if (text.length() > 0) {
                        text = text.substring(0, text.length() - 1);
                        etSearch.setText(text);
                    }
                    if (text.length() > 0) {
                        loadRec(text);
                    }
                } else if (pos == 0) {
                    RemoteDialog remoteDialog = new RemoteDialog(mContext);
                    remoteDialog.show();
                }
            }
        });
        setLoadSir(llLayout);
        this.sKey = (String) SettingsUtil.hkGet(HawkConfig.SEARCH_FILTER_KEY, "");
        String string;
        if (TextUtils.isEmpty(this.sKey)) {
            string = "全局搜索";
        } else if (this.sKey.equals("filter__home")) {
            string = "默认源: " + ApiConfig.get().getHomeSourceBean().getName();
        } else {
            SourceBean sourceBean = ApiConfig.get().getSource(this.sKey);
            string = sourceBean != null ? sourceBean.getName() : "全局搜索";
        }

        filterBtn.setSelected(true);//能动起来的关键代码
        filterBtn.setText(string);
        filterBtn.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                int i;
                List<SourceBean> requestList = new ArrayList<>(ApiConfig.get().getSourceBeanList());
                if (requestList.size() > 0) {
                    ArrayList<SourceBean> siteKey = new ArrayList<>();
                    for (SourceBean bean : requestList) {
                        if (!bean.isSearchable()) {
                            continue;
                        }
                        if (mCheckSources != null && !mCheckSources.containsKey(bean.getKey())) {
                            continue;
                        }
                        siteKey.add(bean);
                    }
                    SourceBean homeSource = ApiConfig.get().getHomeSourceBean();
                    SourceBean gs0 = new SourceBean();
                    gs0.setKey("filter__home");
                    gs0.setName("默认源: " + homeSource.getName());
                    siteKey.remove(homeSource);
                    siteKey.add(0, gs0);
                    SourceBean gs1 = new SourceBean();
                    gs1.setKey("");
                    gs1.setName("全局搜索");
                    siteKey.add(0, gs1);

                    if (TextUtils.isEmpty(sKey)) {
                        i = 0;
                    } else if (sKey.equals("filter__home")) {
                        i = 1;
                    } else {
                        SourceBean sourceBean = ApiConfig.get().getSource(sKey);
                        if (sourceBean != null) {
                            i = siteKey.indexOf(sourceBean);
                        } else {
                            i = -1;
                        }
                    }

                    SelectDialog<SourceBean> dialog = new SelectDialog<>(SearchActivity.this);
                    TvRecyclerView tvRecyclerView = dialog.findViewById(R.id.list);
                    int spanCount;
                    spanCount = (int)Math.floor(siteKey.size()/60.0);
                    spanCount = Math.min(spanCount, 2);
                    tvRecyclerView.setLayoutManager(new V7GridLayoutManager(dialog.getContext(), spanCount+1));
                    ConstraintLayout cl_root = dialog.findViewById(R.id.cl_root);
                    ViewGroup.LayoutParams clp = cl_root.getLayoutParams();
                    clp.width = AutoSizeUtils.mm2px(dialog.getContext(), 380+200*spanCount);
                    dialog.setTip("搜索数据源");
                    dialog.setAdapter(tvRecyclerView, new SelectDialogAdapter.SelectDialogInterface<SourceBean>() {
                        @Override
                        public void click(SourceBean value, int pos) {
                            filterBtn.setText(value.getName());
                            sKey = value.getKey();
                            SettingsUtil.hkPut(HawkConfig.SEARCH_FILTER_KEY, sKey);
                            dialog.dismiss();
                            //search(wd)
                        }

                        @Override
                        public String getDisplay(SourceBean val) {
                            return val.getName();
                        }
                    }, new DiffUtil.ItemCallback<SourceBean>() {
                        @Override
                        public boolean areItemsTheSame(@NonNull @NotNull SourceBean oldItem, @NonNull @NotNull SourceBean newItem) {
                            return oldItem == newItem;
                        }

                        @Override
                        public boolean areContentsTheSame(@NonNull @NotNull SourceBean oldItem, @NonNull @NotNull SourceBean newItem) {
                            return oldItem.getKey().equals(newItem.getKey());
                        }
                    }, siteKey, i);
                    dialog.show();
                } else {
                    Toast.makeText(mContext, "无搜索源", Toast.LENGTH_SHORT).show();
                }
            }
        });

        tvSearchCheckboxBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mSearchCheckboxDialog == null) {
                    List<SourceBean> allSourceBean = ApiConfig.get().getSourceBeanList();
                    List<SourceBean> searchAbleSource = new ArrayList<>();
                    for (SourceBean sourceBean : allSourceBean) {
                        if (sourceBean.isSearchable()) {
                            searchAbleSource.add(sourceBean);
                        }
                    }
                    mSearchCheckboxDialog = new SearchCheckboxDialog(SearchActivity.this, searchAbleSource, mCheckSources);
                }
                mSearchCheckboxDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        dialog.dismiss();
                    }
                });
                mSearchCheckboxDialog.show();
            }
        });
    }

    private void refreshSearchHistory(String keyword2) {
        if (!this.searchPresenter.keywordsExist(keyword2)) {
            this.searchPresenter.addKeyWordsTodb(keyword2);
            initSearchHistory();
        }
    }

    private void initSearchHistory() {
        ArrayList<SearchHistory> searchHistory = this.searchPresenter.getSearchHistory();
        List<String> historyList = new ArrayList<>();
        for (SearchHistory history : searchHistory) {
            historyList.add(history.searchKeyWords);
        }
        Collections.reverse(historyList);
        tv_history.setViews(historyList, new FlowLayout.OnItemClickListener() {
            public void onItemClick(String content) {
                search(content);
                etSearch.setText(content);
                //etSearch.setSelection(etSearch.getText().length());
            }
        });
    }

    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        searchPresenter = new SearchPresenter();
    }

    /**
     * 拼音联想
     */
    private void loadRec(String key) {
//        OkGo.<String>get("https://s.video.qq.com/smartbox")
//                .params("plat", 2)
//                .params("ver", 0)
//                .params("num", 20)
//                .params("otype", "json")
//                .params("query", key)
//                .execute(new AbsCallback<String>() {
//                    @Override
//                    public void onSuccess(Response<String> response) {
//                        try {
//                            ArrayList<String> hots = new ArrayList<>();
//                            String result = response.body();
//                            JsonObject json = JsonParser.parseString(result.substring(result.indexOf("{"), result.lastIndexOf("}") + 1)).getAsJsonObject();
//                            JsonArray itemList = json.get("item").getAsJsonArray();
//                            for (JsonElement ele : itemList) {
//                                JsonObject obj = (JsonObject) ele;
//                                hots.add(obj.get("word").getAsString().trim().replaceAll("<|>|《|》|-", "").split(" ")[0]);
//                            }
//                            wordAdapter.setNewData(hots);
//                        } catch (Throwable th) {
//                            th.printStackTrace();
//                        }
//                    }
//
//                    @Override
//                    public String convertResponse(okhttp3.Response response) throws Throwable {
//                        return response.body().string();
//                    }
//                });
        OkGo.<String>get("https://suggest.video.iqiyi.com/")
                .params("if", "mobile")
                .params("key", key)
                .execute(new AbsCallback<String>() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        try {
                            ArrayList<String> hots = new ArrayList<>();
                            String result = response.body();
                            JsonObject json = JsonParser.parseString(result).getAsJsonObject();
                            JsonArray itemList = json.get("data").getAsJsonArray();
                            for (JsonElement ele : itemList) {
                                JsonObject obj = (JsonObject) ele;
                                hots.add(obj.get("name").getAsString().trim().replaceAll("[<>《》\\-]", ""));
                            }
                            wordAdapter.setNewData(hots);
                        } catch (Throwable th) {
                            th.printStackTrace();
                        }
                    }

                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        return response.body().string();
                    }
                });
    }

    private void initData() {
        showSuccess();
        mGridView.setVisibility(View.GONE);
        refreshQRCode();
        initCheckedSourcesForSearch();
        initSearchHistory();
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("title")) {
            String title = intent.getStringExtra("title");
            showLoading();
            search(title);
        }
        // 加载热词
        OkGo.<String>get("https://node.video.qq.com/x/api/hot_search")
//        OkGo.<String>get("https://api.web.360kan.com/v1/rank")
//                .params("cat", "1")
                .params("channdlId", "0")
                .params("_", System.currentTimeMillis())
                .execute(new AbsCallback<String>() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        try {
                            ArrayList<String> hots = new ArrayList<>();
                            JsonArray itemList = JsonParser.parseString(response.body()).getAsJsonObject().get("data").getAsJsonObject().get("mapResult").getAsJsonObject().get("0").getAsJsonObject().get("listInfo").getAsJsonArray();
//                            JsonArray itemList = JsonParser.parseString(response.body()).getAsJsonObject().get("data").getAsJsonArray();
                            for (JsonElement ele : itemList) {
                                JsonObject obj = (JsonObject) ele;
                                hots.add(obj.get("title").getAsString().trim().replaceAll("[<>《》\\-]", "").split(" ")[0]);
                            }
                            wordAdapter.setNewData(hots);
                        } catch (Throwable th) {
                            th.printStackTrace();
                        }
                    }

                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        return response.body().string();
                    }
                });

    }

    private void refreshQRCode() {
        String address = ControlManager.get().getAddress(false);
        tvAddress.setText(String.format("远程搜索使用手机/电脑扫描下面二维码或者直接浏览器访问地址\n%s", address));
        ivQRCode.setImageBitmap(QRCodeGen.generateBitmap(address, 300, 300));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void server(ServerEvent event) {
        if (event.type == ServerEvent.SERVER_SEARCH) {
            String title = (String) event.obj;
            showLoading();
            search(title);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_SEARCH_RESULT) {
            try {
                searchData(event.obj == null ? null : (AbsXml) event.obj);
            } catch (Exception e) {
                searchData(null);
            }
        }
    }

    private void initCheckedSourcesForSearch() {
        mCheckSources = SearchHelper.getSourcesForSearch();
    }

    private void search(String title) {
        cancel();
        showLoading();
        this.searchTitle = title;
        mGridView.setVisibility(View.GONE);
        searchAdapter.setNewData(new ArrayList<>());
        refreshSearchHistory(title);
        // 分词
        JiebaSegmenter.getJiebaSegmenterSingleton().getDividedStringAsync(searchTitle, new RequestCallback<ArrayList<String>>() {
            @Override
            public void onSuccess(ArrayList<String> result) {
                fenci = result;
                searchResult();
            }

            @Override
            public void onError(String errorMsg) {

            }
        });
        //fenci = JiebaSegmenter.getJiebaSegmenterSingleton().getDividedString(searchTitle);
        //searchResult();
    }

    private AtomicInteger allRunCount = new AtomicInteger(0);

    private void searchResult() {
        try {
            sourceViewModel.initExecutor();
        } catch (Throwable th) {
            th.printStackTrace();
        } finally {
            searchAdapter.setNewData(new ArrayList<>());
            allRunCount.set(0);
        }

        List<SourceBean> searchRequestList = new ArrayList<>();

        boolean equals = this.sKey.equals("filter__home");
        if (equals) {
            SourceBean home = ApiConfig.get().getHomeSourceBean();
            if (home.isSearchable()) {
                searchRequestList.add(home);
            } else {
                Toast.makeText(mContext, "当前源不支持搜索,自动切换到全局搜索", Toast.LENGTH_SHORT).show();
                searchRequestList.addAll(ApiConfig.get().getSourceBeanList());
            }
        } else if (TextUtils.isEmpty(sKey) || ApiConfig.get().getSource(sKey) == null) {
            searchRequestList.addAll(ApiConfig.get().getSourceBeanList());
            SourceBean home = ApiConfig.get().getHomeSourceBean();
            searchRequestList.remove(home);
            searchRequestList.add(0, home);
        } else {
            searchRequestList.add(ApiConfig.get().getSource(sKey));
        }

        ArrayList<String> siteKey = new ArrayList<>();
        for (SourceBean bean : searchRequestList) {
            if (!bean.isSearchable()) {
                continue;
            }
            if (!equals && mCheckSources != null && !mCheckSources.containsKey(bean.getKey())) {
                continue;
            }
            siteKey.add(bean.getKey());
            allRunCount.incrementAndGet();
        }
        if (siteKey.size() <= 0) {
            Toast.makeText(mContext, "没有指定搜索源", Toast.LENGTH_SHORT).show();
            //showEmpty();
            return;
        }

        for (String key : siteKey) {
            sourceViewModel.execute(new Runnable() {
                @Override
                public void run() {
                    sourceViewModel.getSearch(key, searchTitle);
                }
            });
        }
    }

    private void searchData(AbsXml absXml) {
        if (absXml != null && absXml.movie != null && absXml.movie.videoList != null && absXml.movie.videoList.size() > 0) {
            List<Movie.Video> data = new ArrayList<>();
            for (Movie.Video video : absXml.movie.videoList) {
                if (SearchHelper.searchContains(video.name, fenci)) {
                    data.add(video);
                }
            }
            if (searchAdapter.getData().size() > 0) {
                searchAdapter.addData(data);
            } else {
                showSuccess();
                searchAdapter.setNewData(data);
                tv_history.setVisibility(View.GONE);
                searchTips.setVisibility(View.GONE);
                mGridView.setVisibility(View.VISIBLE);
            }
        }

        int count = allRunCount.decrementAndGet();
        if (count <= 0) {
            if (searchAdapter.getData().size() <= 0) {
                showEmpty();
            }
            cancel();
        }
    }


    private void cancel() {
        OkGo.getInstance().cancelTag("search");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancel();
        try {
            if (sourceViewModel != null) {
                sourceViewModel.shutdownNow();
                sourceViewModel.destroyExecutor();
                JsLoader.load();
                PythonLoader.getInstance().load();
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        EventBus.getDefault().unregister(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onInputMsgEvent(InputMsgEvent inputMsgEvent) {
        /*View vFocus = SearchActivity.this.getWindow().getDecorView().findFocus();
        if (vFocus.getTag() == etSearch) {
            ((EditText) vFocus).setText(inputMsgEvent.getText());
        }*/
        etSearch.setFocusableInTouchMode(true);
        etSearch.requestFocus();
        etSearch.setText(inputMsgEvent.getText());
        search(inputMsgEvent.getText());
    }

//    @Override
//    public boolean dispatchKeyEvent(KeyEvent event) {
//        if (event.getAction() == KeyEvent.ACTION_DOWN) {
//            int keyCode = event.getKeyCode();
//            if (keyCode == KeyEvent.KEYCODE_MENU) {
//                if(!hasKeyBoard)enableKeyboard(SearchActivity.this);
//                openSystemKeyBoard();//再次尝试拉起键盘
//                getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
//            }
//        } else if (event.getAction() == KeyEvent.ACTION_UP) {
//        }
//        return super.dispatchKeyEvent(event);
//    }
}