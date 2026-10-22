package person.notfresh.readingshare.ui.settings;

import static android.app.Activity.RESULT_OK;

import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import android.app.AlertDialog;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.MainActivity;
import person.notfresh.readingshare.LinksHistoryActivity;
import person.notfresh.readingshare.EventLogActivity;
import person.notfresh.readingshare.model.LinkItem;
import person.notfresh.readingshare.model.LinkJson;
import person.notfresh.readingshare.db.LinkDao;
import person.notfresh.readingshare.db.SubjectDao;
import person.notfresh.readingshare.db.DbConnection;
import person.notfresh.readingshare.core.model.Subject;
import person.notfresh.readingshare.model.ReaderSourceSpec;
import person.notfresh.readingshare.util.ReaderSourceStore;
import person.notfresh.readingshare.eventlog.EventLogClient;
import person.notfresh.readingshare.db.SearchHistoryManager;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.widget.TextView;
import android.content.pm.PackageManager;
import com.google.android.material.snackbar.Snackbar;
import person.notfresh.readingshare.util.ExportUtil;
import person.notfresh.readingshare.util.ImportUtil;
import person.notfresh.readingshare.util.RecentTagsManager;
import person.notfresh.readingshare.util.ShareUtil;
import person.notfresh.readingshare.util.SubjectUtil;
import com.google.android.material.textfield.TextInputEditText;
import person.notfresh.readingshare.adapter.OverrideRuleAdapter;
import person.notfresh.readingshare.model.OverrideAction;
import person.notfresh.readingshare.dialog.AddOverrideRuleDialog;
import person.notfresh.readingshare.model.WebViewOverrideRule;
import person.notfresh.readingshare.util.WebViewOverrideRules;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import person.notfresh.readingshare.sync.SimpleSyncManager;

public class SettingFragment extends Fragment implements AddOverrideRuleDialog.OnRuleSubmitListener {

    private static final int REQUEST_CODE_IMPORT_FILE = 1; // 改为通用文件导入
    private static final String DEFAULT_SERVER_URL = "https://duxiang.ai";

    private SettingDropdown defaultTabDropdown;
    private SettingDropdown recentTagsWindowDropdown;
    private LinkDao linkDao; // 声明 LinkDao
    private TextInputEditText serverUrlInput;
    private TextInputEditText syncSecretKeyInput;
    private SimpleSyncManager syncManager;
    private android.widget.TextView syncStatusText;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_slideshow, container, false);

        // 初始化 LinkDao
        linkDao = new LinkDao(requireContext());
        linkDao.open();

        // 初始化同步管理器
        syncManager = new SimpleSyncManager(requireContext());

        // 默认 Tab 下拉（2.3.19.8：与「阅读数据来源」同一套 Material exposed dropdown）
        final String[] tabLabels = getResources().getStringArray(R.array.default_tabs_array);
        final List<String> tabOptions = new ArrayList<>(Arrays.asList(tabLabels));

        // 加载保存的默认 Tab
        SharedPreferences prefs = requireActivity().getPreferences(Context.MODE_PRIVATE);
        int defaultTab = prefs.getInt("default_tab", 0);

        // 处理已废弃的标签页选项（原索引1）的迁移
        // 原索引：0=首页, 1=标签(已废弃), 2=主题, 3=RSS, 4=随机
        // 新索引：0=首页, 1=主题, 2=RSS, 3=随机
        int adjustedTab = defaultTab;
        if (defaultTab == 1) {
            // 原标签页选项，迁移到首页
            adjustedTab = 0;
            // 更新保存的值
            prefs.edit().putInt("default_tab", 0).apply();
        } else if (defaultTab == 2) {
            // 原主题选项，索引减1
            adjustedTab = 1;
        } else if (defaultTab == 3) {
            // 原RSS选项，索引减1
            adjustedTab = 2;
        } else if (defaultTab == 4) {
            // 随机
            adjustedTab = 3;
        } else if (defaultTab == 5) {
            // 阅读(2.3.19 新增,原索引方案里没有这一项)
            adjustedTab = 4;
        }

        // 兜底：避免越界导致崩溃
        if (adjustedTab < 0 || adjustedTab >= tabOptions.size()) {
            adjustedTab = 0;
        }

        ViewGroup defaultTabHost = root.findViewById(R.id.default_tab_dropdown_host);
        defaultTabDropdown = SettingDropdown.attach(requireContext(), defaultTabHost)
                .setHint(R.string.setting_default_tab_hint);
        defaultTabDropdown.setOptions(tabOptions, adjustedTab);

        // 保存用户选择
        defaultTabDropdown.setOnSelected(position -> {
            SharedPreferences.Editor editor = prefs.edit();
            // 将新索引映射回原索引格式（用于MainActivity的兼容性）
            // 新索引：0=首页, 1=主题, 2=RSS, 3=随机
            // 原索引：0=首页, 2=主题, 3=RSS（跳过已废弃的1=标签）, 4=随机
            int savedTab;
            if (position == 0) {
                savedTab = 0; // 首页
            } else if (position == 1) {
                savedTab = 2; // 链接
            } else if (position == 2) {
                savedTab = 3; // RSS
            } else if (position == 3) {
                savedTab = 4; // 随机
            } else {
                savedTab = 5; // 阅读(2.3.19)
            }
            editor.putInt("default_tab", savedTab);
            editor.apply();
        });

        // 最近标签窗口大小下拉（2.3.19.8 同上）
        final String[] windowLabels = getResources().getStringArray(R.array.recent_tags_window_array);
        final List<String> windowOptions = new ArrayList<>(Arrays.asList(windowLabels));

        // 加载保存的窗口大小，保存值-3 = 索引
        int savedWindow = RecentTagsManager.getRecentTagsWindow(requireContext());
        int windowIndex = Math.max(0, Math.min(windowOptions.size() - 1, savedWindow - 3));

        ViewGroup windowHost = root.findViewById(R.id.recent_tags_window_dropdown_host);
        recentTagsWindowDropdown = SettingDropdown.attach(requireContext(), windowHost)
                .setHint(R.string.setting_recent_tags_window_hint);
        recentTagsWindowDropdown.setOptions(windowOptions, windowIndex);

        recentTagsWindowDropdown.setOnSelected(position -> {
            int window = position + 3;  // 索引+3 = 实际窗口值
            RecentTagsManager.setRecentTagsWindow(requireContext(), window);
        });

        // 初始化服务器URL输入框
        serverUrlInput = root.findViewById(R.id.server_url_input);

        // 从 SharedPreferences 加载保存的URL（统一读 simple_sync，与 SimpleSyncManager
        // 一致 — 修复 URL 改动不生效 bug，之前读 settings 与 simple_sync 分离）
        SharedPreferences globalPrefs = requireActivity().getSharedPreferences("settings", Context.MODE_PRIVATE);
        String savedUrl = SimpleSyncManager.getServerUrl(requireContext());
        if (savedUrl.isEmpty()) {
            savedUrl = DEFAULT_SERVER_URL;
        }
        serverUrlInput.setText(savedUrl);

        CheckBox showMailFabCheckbox = root.findViewById(R.id.show_mail_fab_checkbox);
        showMailFabCheckbox.setChecked(globalPrefs.getBoolean("show_mail_fab", false));
        showMailFabCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            globalPrefs.edit().putBoolean("show_mail_fab", isChecked).apply();
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).updateMailFabVisibility();
            }
        });

        CheckBox showRandomEntryCheckbox = root.findViewById(R.id.show_random_entry_checkbox);
        showRandomEntryCheckbox.setChecked(globalPrefs.getBoolean("show_random_fab", true));
        showRandomEntryCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            globalPrefs.edit().putBoolean("show_random_fab", isChecked).apply();
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).updateRandomFabVisibility();
            }
        });

        // 左手/右手模式:默认右手,切到左手时 fab 区域跑到左边
        RadioButton fabSideRightRb = root.findViewById(R.id.fab_side_right);
        RadioButton fabSideLeftRb = root.findViewById(R.id.fab_side_left);
        RadioGroup fabSideGroup = root.findViewById(R.id.fab_side_group);
        boolean isLeft = globalPrefs.getBoolean("fab_side_left", false);
        fabSideRightRb.setChecked(!isLeft);
        fabSideLeftRb.setChecked(isLeft);
        fabSideGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean left = (checkedId == R.id.fab_side_left);
            globalPrefs.edit().putBoolean("fab_side_left", left).apply();
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).applyFabSide();
            }
        });

        root.findViewById(R.id.button_link_history).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), LinksHistoryActivity.class)));

        root.findViewById(R.id.button_event_log).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), EventLogActivity.class)));

        // 「关于」弹窗(简化版:app 名 + 版本 + GitHub 链接下划线)
        root.findViewById(R.id.button_about).setOnClickListener(v -> showAboutDialog());

        // 监听输入框内容变化 — 实时保存(每改一个字符就 apply 一次)。
        // 顺序很重要:setText(savedUrl) 在 addTextChangedListener 之前调用,
        // 避免"程序自己设值"被 watcher 当成"用户修改"白白触发一次保存。
        // 空字符串跳过:保留 SP 里原有的 DEFAULT_SERVER_URL,避免用户清空 URL 后
        // 下次启动同步炸。
        serverUrlInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                String text = s.toString().trim();
                if (text.isEmpty()) return; // 空跳过,不动 SP
                SimpleSyncManager.saveServerUrl(requireContext(), text);
            }
        });

        // 初始化同步密钥输入框
        syncSecretKeyInput = root.findViewById(R.id.sync_secret_key_input);
        syncStatusText = root.findViewById(R.id.sync_status_text);

        // 加载保存的密钥(同样:setText 在 watcher 注册之前)
        String savedKey = SimpleSyncManager.getSecretKey(requireContext());
        syncSecretKeyInput.setText(savedKey);

        // 实时保存 secret — 不跳过空字符串(secret 没默认值,清空就是清空)。
        // 必须 trim,防 commit 8ce1089 修的"换行污染 Authorization 头"bug。
        syncSecretKeyInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                String text = s.toString().trim();
                SimpleSyncManager.saveSecretKey(requireContext(), text);
            }
        });

        // 同步按钮点击事件（已删除 — 同步入口改到 EventLogActivity 内的"立即同步"按钮，
        //               由 EventLogClient.pushPending + pull 驱动；不再走 SimpleSyncManager.performSync）

        // 阅读模式设置（normal / smooth）
        RadioGroup readingModeGroup = root.findViewById(R.id.reading_mode_group);
        RadioButton normalRb = root.findViewById(R.id.reading_mode_normal);
        RadioButton smoothRb = root.findViewById(R.id.reading_mode_smooth);

        String readingMode = globalPrefs.getString("reading_mode", "normal");
        if ("smooth".equals(readingMode)) {
            smoothRb.setChecked(true);
        } else {
            normalRb.setChecked(true);
        }

        readingModeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            SharedPreferences.Editor editor = globalPrefs.edit();
            if (checkedId == R.id.reading_mode_smooth) {
                editor.putString("reading_mode", "smooth");
            } else {
                editor.putString("reading_mode", "normal");
            }
            editor.apply();
        });

        // 翻页按钮显示方式设置（bottom / floating / both）
        RadioGroup navButtonStyleGroup = root.findViewById(R.id.nav_button_style_group);
        RadioButton bottomRb = root.findViewById(R.id.nav_button_style_bottom);
        RadioButton floatingRb = root.findViewById(R.id.nav_button_style_floating);
        RadioButton bothRb = root.findViewById(R.id.nav_button_style_both);

        String navButtonStyle = globalPrefs.getString("nav_button_style", "both");
        if ("bottom".equals(navButtonStyle)) {
            bottomRb.setChecked(true);
        } else if ("floating".equals(navButtonStyle)) {
            floatingRb.setChecked(true);
        } else {
            bothRb.setChecked(true);
        }

        navButtonStyleGroup.setOnCheckedChangeListener((group, checkedId) -> {
            SharedPreferences.Editor editor = globalPrefs.edit();
            if (checkedId == R.id.nav_button_style_bottom) {
                editor.putString("nav_button_style", "bottom");
            } else if (checkedId == R.id.nav_button_style_floating) {
                editor.putString("nav_button_style", "floating");
            } else {
                editor.putString("nav_button_style", "both");
            }
            editor.apply();
        });

        // 外部链接打开模式设置
        RadioGroup externalLinkModeGroup = root.findViewById(R.id.external_link_mode_group);
        RadioButton confirmRb = root.findViewById(R.id.external_link_mode_confirm);
        RadioButton directRb = root.findViewById(R.id.external_link_mode_direct);
        RadioButton blockRb = root.findViewById(R.id.external_link_mode_block);

        int externalLinkMode = globalPrefs.getInt("external_link_mode", 0);
        if (externalLinkMode == 1) {
            directRb.setChecked(true);
        } else if (externalLinkMode == 2) {
            blockRb.setChecked(true);
        } else {
            confirmRb.setChecked(true);
        }

        externalLinkModeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            SharedPreferences.Editor editor = globalPrefs.edit();
            if (checkedId == R.id.external_link_mode_direct) {
                editor.putInt("external_link_mode", 1);
            } else if (checkedId == R.id.external_link_mode_block) {
                editor.putInt("external_link_mode", 2);
            } else {
                editor.putInt("external_link_mode", 0);
            }
            editor.apply();
        });

        // 拦截名单(白名单)区块 —— 每行 = pattern + 3 选 1 + 删除 + 底部 +号新增
        bindOverrideRulesSection(root);

        // 添加导入文件按钮
        root.findViewById(R.id.button_import_csv).setOnClickListener(v -> {
            importFile();  // 支持 CSV 和 JSON
        });

        // 添加导出按钮的点击事件
        root.findViewById(R.id.button_export).setOnClickListener(v -> {
            showExportDialog();
        });

        // 搜索历史 maxCount
        TextInputEditText maxCountInput = root.findViewById(R.id.search_history_max_count_input);
        SearchHistoryManager historyManager = new SearchHistoryManager(requireContext());
        maxCountInput.setText(String.valueOf(historyManager.getMaxCount()));
        maxCountInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String raw = maxCountInput.getText().toString().trim();
                int count;
                try {
                    count = Integer.parseInt(raw);
                } catch (NumberFormatException e) {
                    count = 10;
                }
                if (count < 1) count = 1;
                if (count > 100) count = 100;
                historyManager.setMaxCount(count);
                maxCountInput.setText(String.valueOf(count));
                Snackbar.make(requireView(), getString(R.string.setting_snackbar_saved_history, count), Snackbar.LENGTH_SHORT).show();
            }
        });

        // 语言切换区块 (2.3.18 新增)：见 bindLanguageSection 方法。
        bindLanguageSection(root);

        // 阅读页数据来源区块 (2.3.19 新增)：UI 拆到 ReaderSourceSectionBinder，避免本类继续膨胀。
        ReaderSourceSectionBinder.bind(this, root);

        return root;
    }

    /**
     * 语言切换区块：动态添加标题 + 3 选 1 RadioGroup 到设置页底部。
     * 通过 AppCompatDelegate.setApplicationLocales() 切换；该 API 在 API 33+ 写系统设置，
     * 老版本由 androidx 自己持久化。系统会自动 recreate 当前 Activity。
     */
    private void bindLanguageSection(View root) {
        android.widget.ScrollView scroll = (root instanceof android.widget.ScrollView)
                ? (android.widget.ScrollView) root : null;
        android.view.ViewGroup container = null;
        if (scroll != null && scroll.getChildCount() > 0) {
            android.view.View child = scroll.getChildAt(0);
            if (child instanceof android.view.ViewGroup) container = (android.view.ViewGroup) child;
        }
        if (container == null) return;

        android.widget.TextView title = new android.widget.TextView(requireContext());
        title.setText(R.string.language_section_title);
        title.setTextSize(18f);
        android.widget.LinearLayout.LayoutParams titleLp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = (int) (24 * getResources().getDisplayMetrics().density);
        titleLp.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
        title.setLayoutParams(titleLp);
        container.addView(title);

        android.widget.RadioGroup group = new android.widget.RadioGroup(requireContext());
        group.setOrientation(android.widget.RadioGroup.VERTICAL);
        android.widget.LinearLayout.LayoutParams groupLp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        group.setLayoutParams(groupLp);

        String[] labels = new String[]{
                getString(R.string.language_option_system),
                getString(R.string.language_option_chinese),
                getString(R.string.language_option_english)
        };
        String[] tags = new String[]{"", "zh", "en"};
        android.widget.RadioButton[] buttons = new android.widget.RadioButton[labels.length];

        androidx.core.os.LocaleListCompat current = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales();
        String currentTag = current.toLanguageTags().toLowerCase();
        int currentIdx = 0;
        if (currentTag.startsWith("zh")) {
            currentIdx = 1;
        } else if (currentTag.startsWith("en")) {
            currentIdx = 2;
        }

        for (int i = 0; i < labels.length; i++) {
            android.widget.RadioButton rb = new android.widget.RadioButton(requireContext());
            rb.setText(labels[i]);
            rb.setId(android.view.View.generateViewId());
            rb.setTag(tags[i]);
            rb.setChecked(i == currentIdx);
            group.addView(rb);
            buttons[i] = rb;
        }

        group.setOnCheckedChangeListener((g, checkedId) -> {
            for (int i = 0; i < buttons.length; i++) {
                if (buttons[i].getId() == checkedId) {
                    String tag = (String) buttons[i].getTag();
                    androidx.core.os.LocaleListCompat target = (tag == null || tag.isEmpty())
                            ? androidx.core.os.LocaleListCompat.getEmptyLocaleList()
                            : androidx.core.os.LocaleListCompat.forLanguageTags(tag);
                    androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(target);
                    return;
                }
            }
        });

        container.addView(group);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (linkDao != null) {
            linkDao.close(); // 关闭数据库连接
        }
        if (syncManager != null) {
            syncManager.close();
        }
    }

    /**
     * 执行同步操作
     */
    private void performSync() {
        syncStatusText.setText(R.string.setting_sync_status_syncing);

        new Thread(() -> {
            SimpleSyncManager.SyncResult result = syncManager.sync();

            requireActivity().runOnUiThread(() -> {
                if (result.success) {
                    String status = getString(R.string.setting_sync_status_ok,
                            result.uploadedCount, result.downloadedCount);
                    syncStatusText.setText(status);
                } else {
                    syncStatusText.setText(getString(R.string.setting_sync_status_failed, result.message));
                }
            });
        }).start();
    }

    // 添加导入文件的方法（支持 CSV 和 JSON）
    private void importFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        // 使用通配符，支持多种文件类型
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, getString(R.string.setting_chooser_csv_or_json)), REQUEST_CODE_IMPORT_FILE);
    }

    private Uri pendingImportUri; // 待导入的文件 URI

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_IMPORT_FILE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                // 保存 URI，显示去重选项对话框
                pendingImportUri = uri;
                showImportOptionsDialog();
            }
        }
    }

    /**
     * 显示导入选项对话框（去重选项）
     */
    private void showImportOptionsDialog() {
        // 创建复选框
        CheckBox checkBox = new CheckBox(requireContext());
        checkBox.setText(R.string.setting_import_dedup_checkbox);
        checkBox.setChecked(true); // 默认勾选去重

        // 创建布局容器
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        layout.addView(checkBox);

        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.setting_import_dialog_title)
            .setView(layout)
            .setPositiveButton(R.string.link_dialog_ok, (dialog, which) -> {
                boolean removeDuplicates = checkBox.isChecked();
                performImport(removeDuplicates);
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    /**
     * 执行导入操作
     * @param removeDuplicates 是否去重
     */
    private void performImport(boolean removeDuplicates) {
        if (pendingImportUri == null) {
            return;
        }
        
        // 使用 ImportUtil 导入文件
        ImportUtil.ImportResult result = ImportUtil.importFromUri(requireContext(), pendingImportUri);
        
        if (result.items == null || result.items.isEmpty()) {
            Snackbar.make(requireView(), R.string.setting_import_no_links, Snackbar.LENGTH_SHORT).show();
            return;
        }
        
        // 如果启用去重，过滤掉已存在的链接
        List<LinkItem> itemsToImport = result.items;
        // 与 itemsToImport 对齐的 subject 名字列表(只有真正会写入的 item 才保留)
        List<List<String>> subjectsToImport = result.subjectsPerItem;
        int duplicateCount = 0;

        if (removeDuplicates) {
            List<LinkItem> filteredItems = new ArrayList<>();
            List<List<String>> filteredSubjects = new ArrayList<>();
            for (int i = 0; i < itemsToImport.size(); i++) {
                LinkItem item = itemsToImport.get(i);
                List<String> subjects = i < subjectsToImport.size()
                        ? subjectsToImport.get(i) : java.util.Collections.emptyList();
                if (!linkDao.urlExists(item.getUrl())) {
                    filteredItems.add(item);
                    filteredSubjects.add(subjects);
                } else {
                    duplicateCount++;
                }
            }
            itemsToImport = filteredItems;
            subjectsToImport = filteredSubjects;
        }

        // 将导入的链接保存到数据库
        int importedCount = 0;
        for (int i = 0; i < itemsToImport.size(); i++) {
            LinkItem item = itemsToImport.get(i);
            try {
                linkDao.insertLink(item);
                EventLogClient.get().create("links",
                        String.valueOf(item.getId()),
                        item.getTimestamp(),
                        LinkJson.toJsonString(item));
                // 关联 subject(若导出时带过来)— 已存在复用,不存在新建
                List<String> subjects = i < subjectsToImport.size()
                        ? subjectsToImport.get(i) : java.util.Collections.emptyList();
                if (subjects != null && !subjects.isEmpty()) {
                    java.util.Map<String, Long> nameToId = SubjectUtil.resolveOrCreateSubjects(
                            requireContext(), subjects);
                    for (Long subjectId : nameToId.values()) {
                        if (subjectId != null) {
                            SubjectUtil.linkLinkToSubject(requireContext(), item.getId(), subjectId);
                        }
                    }
                }
                importedCount++;
            } catch (Exception e) {
                // 忽略插入失败的情况
            }
        }
        
        // 显示导入结果
        Snackbar.make(requireView(),
                getString(R.string.setting_import_summary,
                        result.format,
                        importedCount,
                        removeDuplicates ? duplicateCount : 0,
                        result.errorCount),
                Snackbar.LENGTH_LONG).show();
        
        // 清空待导入的 URI
        pendingImportUri = null;
    }

    private void showExportDialog() {
        // 使用 XML 布局文件
        View dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_export_options, null);
        
        RadioGroup formatGroup = dialogView.findViewById(R.id.format_group);
        RadioGroup actionGroup = dialogView.findViewById(R.id.action_group);
        RadioButton csvRadio = dialogView.findViewById(R.id.radio_csv);
        RadioButton jsonRadio = dialogView.findViewById(R.id.radio_json);
        RadioButton shareRadio = dialogView.findViewById(R.id.radio_share);
        RadioButton saveRadio = dialogView.findViewById(R.id.radio_save);
        
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
            .setTitle(R.string.setting_export_chooser_title)
            .setView(dialogView)
            .setPositiveButton(R.string.link_dialog_ok, (d, which) -> {
                boolean isJson = (formatGroup.getCheckedRadioButtonId() == jsonRadio.getId());
                boolean isSave = (actionGroup.getCheckedRadioButtonId() == saveRadio.getId());

                if (isSave) {
                    exportAndSave(isJson);
                } else {
                    exportAndShare(isJson);
                }
            })
            .setNegativeButton(R.string.cancel, null)
            .create();
        
        dialog.show();
    }

    /**
     * 导出并分享文件
     */
    private void exportAndShare(boolean isJson) {
        // 显示进度对话框
        ProgressDialog progressDialog = new ProgressDialog(requireContext());
        progressDialog.setMessage("正在导出数据...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        // 在后台线程执行导出操作
        new Thread(() -> {
            boolean success = false;
            Uri fileUri = null;
            String errorMessage = null;
            
            try {
                List<LinkItem> exportList = linkDao.getAllLinks();
                Map<Long, List<String>> linkIdToSubjectNames = buildSubjectMap(exportList);
                fileUri = ExportUtil.exportToPublicDirectory(
                    requireContext(),
                    exportList,
                    isJson,
                    linkIdToSubjectNames
                );
                success = true;
            } catch (Exception e) {
                Log.e("SettingFragment", "导出失败", e);
                errorMessage = e.getMessage();
            }
            
            final Uri finalFileUri = fileUri;
            final boolean finalSuccess = success;
            final String finalErrorMessage = errorMessage;
            
            // 在 UI 线程中更新界面
            requireActivity().runOnUiThread(() -> {
                progressDialog.dismiss();
                
                if (finalSuccess && finalFileUri != null) {
                    // 保存成功后，分享文件
                    try {
                        Intent shareIntent = new Intent(Intent.ACTION_SEND);
                        shareIntent.setType(isJson ? "text/plain" : "text/csv");
                        shareIntent.putExtra(Intent.EXTRA_STREAM, finalFileUri);
                        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(shareIntent, "分享导出文件"));
                    } catch (Exception e) {
                        Snackbar.make(requireView(), 
                            "分享失败：" + e.getMessage(), 
                            Snackbar.LENGTH_LONG).show();
                    }
                } else {
                    Snackbar.make(requireView(), 
                        "导出失败：" + (finalErrorMessage != null ? finalErrorMessage : "请重试"), 
                        Snackbar.LENGTH_LONG).show();
                }
            });
        }).start();
    }
    
    /**
     * 导出并保存到公共 Documents 目录（新逻辑）
     */
    private void exportAndSave(boolean isJson) {
        // 显示进度对话框
        ProgressDialog progressDialog = new ProgressDialog(requireContext());
        progressDialog.setMessage("正在导出数据...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        // 在后台线程执行导出操作
        new Thread(() -> {
            boolean success = false;
            Uri fileUri = null;
            String errorMessage = null;
            
            try {
                List<LinkItem> exportList = linkDao.getAllLinks();
                Map<Long, List<String>> linkIdToSubjectNames = buildSubjectMap(exportList);
                fileUri = ExportUtil.exportToPublicDirectory(
                    requireContext(),
                    exportList,
                    isJson,
                    linkIdToSubjectNames
                );
                success = true;
            } catch (Exception e) {
                Log.e("SettingFragment", "导出失败", e);
                errorMessage = e.getMessage();
            }
            
            final Uri finalFileUri = fileUri;
            final boolean finalSuccess = success;
            final String finalErrorMessage = errorMessage;
            
            // 在 UI 线程中更新界面
            requireActivity().runOnUiThread(() -> {
                progressDialog.dismiss();
                
                if (finalSuccess) {
                    showExportSuccessDialog(isJson, finalFileUri);
                } else {
                    Snackbar.make(requireView(), 
                        "导出失败：" + (finalErrorMessage != null ? finalErrorMessage : "请重试"), 
                        Snackbar.LENGTH_LONG).show();
                }
            });
        }).start();
    }
    
    /**
     * 显示导出成功对话框
     */
    private void showExportSuccessDialog(boolean isJson, Uri fileUri) {
        String format = isJson ? "JSON" : "CSV";
        new AlertDialog.Builder(requireContext())
            .setTitle("导出成功")
            .setMessage(format + " 文件已保存到 Documents 目录")
            .setPositiveButton("打开文件", (dialog, which) -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(fileUri, isJson ? "text/plain" : "text/csv");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(requireContext(), 
                        "无法打开文件：" + e.getMessage(), 
                        Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("确定", null)
            .show();
    }

    // 添加获取服务器URL的公共方法
    public static String getServerUrl(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
            context.getPackageName() + "_preferences",
            Context.MODE_PRIVATE
        );
        return prefs.getString("server_url", DEFAULT_SERVER_URL);
    }

    /**
     * 拦截名单(白名单)section 绑定:RecyclerView 渲染 + 增改删回调 + 复用
     * {@link AddOverrideRuleDialog} 作为新增入口(SetFragment 是 DialogFragment 的 parent fragment)。
     */
    private void bindOverrideRulesSection(View root) {
        RecyclerView recycler = root.findViewById(R.id.override_rule_recycler);
        TextView emptyHint = root.findViewById(R.id.override_rule_empty_hint);
        Button addBtn = root.findViewById(R.id.override_rule_add_btn);

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        final Context ctx = requireContext();
        // 主真相源在 adapter.rules(外部传入),但 LOAD 时从 SP 现读
        // 提前 final 化让 inner anonymous OnRuleChangeListener 可以引用
        final OverrideRuleAdapter[] adapterRef = new OverrideRuleAdapter[1];
        adapterRef[0] = new OverrideRuleAdapter(new OverrideRuleAdapter.OnRuleChangeListener() {
            @Override
            public void onRuleActionChanged(int position, OverrideAction newAction) {
                try {
                    List<WebViewOverrideRule> all = WebViewOverrideRules.loadAll(ctx);
                    if (position < 0 || position >= all.size()) return;
                    WebViewOverrideRule old = all.get(position);
                    WebViewOverrideRule updated = new WebViewOverrideRule(old.getContextUrl(), old.getRedirectedUrl(), newAction);
                    WebViewOverrideRules.update(ctx, position, updated);
                    Snackbar.make(requireView(), "已更新: " + updated.getContextUrl(), Snackbar.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Snackbar.make(requireView(), "更新失败: " + e.getMessage(), Snackbar.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onRuleDeleteClicked(int position) {
                try {
                    List<WebViewOverrideRule> all = WebViewOverrideRules.loadAll(ctx);
                    if (position < 0 || position >= all.size()) return;
                    String deletedContextUrl = all.get(position).getContextUrl();
                    WebViewOverrideRules.remove(ctx, position);
                    refreshOverrideRulesList(adapterRef[0], emptyHint);
                    Snackbar.make(requireView(), "已删除: " + deletedContextUrl, Snackbar.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Snackbar.make(requireView(), "删除失败: " + e.getMessage(), Snackbar.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onRuleViewDetailClicked(int position) {
                List<WebViewOverrideRule> all = WebViewOverrideRules.loadAll(ctx);
                if (position < 0 || position >= all.size()) return;
                showRuleDetailDialog(all.get(position));
            }
        });
        recycler.setAdapter(adapterRef[0]);
        refreshOverrideRulesList(adapterRef[0], emptyHint);

        addBtn.setOnClickListener(v ->
                AddOverrideRuleDialog.showDialog(getChildFragmentManager(), null));
    }

    private void refreshOverrideRulesList(OverrideRuleAdapter adapter, TextView emptyHint) {
        List<WebViewOverrideRule> all = WebViewOverrideRules.loadAll(requireContext());
        adapter.setRules(all);
        emptyHint.setVisibility(all.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /**
     * 拦截规则查看详情弹窗(只读):显示完整 contextUrl + redirectedUrl + action,
     * 不用 ScrollingTextView 是因为短链太长,选 AlertDialog 自带 TextView + 项目内通用
     * `AlertDialog.Builder.setMessage(multi-line)` 行为即可。
     */
    private void showRuleDetailDialog(WebViewOverrideRule rule) {
        String ctx = rule.getContextUrl();
        String redirected = rule.getRedirectedUrl();
        String action = rule.getAction().name();
        StringBuilder message = new StringBuilder();
        message.append("原始 URL:\n").append(ctx).append("\n\n");
        if (redirected != null && !redirected.isEmpty()) {
            message.append("重定向后 URL:\n").append(redirected).append("\n\n");
        }
        message.append("动作: ").append(action);
        new AlertDialog.Builder(requireContext())
                .setTitle("拦截规则详情")
                .setMessage(message.toString())
                .setPositiveButton("关闭", null)
                .show();
    }

    /**
     * AddOverrideRuleDialog 父 fragment 回调:抽屉入口(无 prefill URL)。
     */
    @Override
    public void onRuleSubmitted(WebViewOverrideRule rule) {
        try {
            WebViewOverrideRules.add(requireContext(), rule);
            Snackbar.make(requireView(), "已添加: " + rule.getContextUrl(), Snackbar.LENGTH_SHORT).show();
            // 重新读 + 重绘
            RecyclerView recycler = requireView().findViewById(R.id.override_rule_recycler);
            TextView emptyHint = requireView().findViewById(R.id.override_rule_empty_hint);
            if (recycler.getAdapter() instanceof OverrideRuleAdapter) {
                refreshOverrideRulesList((OverrideRuleAdapter) recycler.getAdapter(), emptyHint);
            }
        } catch (IllegalArgumentException e) {
            Snackbar.make(requireView(), e.getMessage(), Snackbar.LENGTH_SHORT).show();
        }
    }

    /** 给一组 link 算出 linkId → subject 名字 映射,供 ExportUtil 带上 subject 列 */
    private Map<Long, List<String>> buildSubjectMap(List<LinkItem> items) {
        if (items == null || items.isEmpty()) return Collections.emptyMap();
        List<Long> ids = new ArrayList<>(items.size());
        for (LinkItem item : items) {
            if (item != null && item.getId() > 0) ids.add(item.getId());
        }
        return SubjectUtil.getSubjectNamesByLinkIds(requireContext(), ids);
    }

    /**
     * 「关于」弹窗:app 名 + 版本号 + GitHub 链接(下划线,可点击跳转)
     * 仿 EventLogActivity.makeClickableHint 的样式(SpannableString + URLSpan)
     */
    private void showAboutDialog() {
        String appName = getString(R.string.app_name);
        // debug 构建默认不生成 BuildConfig,用 PackageManager 拿 versionName(通用方案)
        String version;
        try {
            version = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            version = "?";
        }
        String url = "https://github.com/notfresh/reading-share-android";

        // "读享\n版本 2.3.12.1\n\n项目主页 https://github.com/..." —— 最后一行整体可点
        String line1 = appName;
        String line2 = "版本 " + version;
        String linkText = url;
        String full = line1 + "\n" + line2 + "\n\n" + linkText;

        SpannableString ss = new SpannableString(full);
        // 让「app 名」加粗(让用户一眼看到名字)
        ss.setSpan(new StyleSpan(Typeface.BOLD), 0, line1.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        // 让最后一行(url)是下划线 + 可点击
        int linkStart = line1.length() + 1 + line2.length() + 2;  // "\n\n" 后
        int linkEnd = full.length();
        ss.setSpan(new URLSpan(url), linkStart, linkEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        TextView tv = new TextView(requireContext());
        tv.setText(ss);
        tv.setTextIsSelectable(true);
        tv.setPadding(48, 32, 48, 32);
        tv.setLinksClickable(true);
        tv.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());

        new AlertDialog.Builder(requireContext())
            .setTitle("关于")
            .setView(tv)
            .setPositiveButton("关闭", null)
            .show();
    }
}