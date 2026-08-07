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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;
import android.app.AlertDialog;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.model.LinkItem;
import person.notfresh.readingshare.db.LinkDao;
import person.notfresh.readingshare.db.SearchHistoryManager;
import com.google.android.material.snackbar.Snackbar;
import person.notfresh.readingshare.util.ExportUtil;
import person.notfresh.readingshare.util.ImportUtil;
import person.notfresh.readingshare.util.RecentTagsManager;
import person.notfresh.readingshare.util.ShareUtil;
import com.google.android.material.textfield.TextInputEditText;

import person.notfresh.readingshare.sync.SimpleSyncManager;

public class SettingFragment extends Fragment {

    private static final int REQUEST_CODE_IMPORT_FILE = 1; // 改为通用文件导入
    private static final String DEFAULT_SERVER_URL = "https://duxiang.ai";

    private Spinner defaultTabSpinner;
    private Spinner recentTagsWindowSpinner;
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

        defaultTabSpinner = root.findViewById(R.id.default_tab_spinner);

        // 设置 Spinner 的选项
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(getContext(),
                R.array.default_tabs_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        defaultTabSpinner.setAdapter(adapter);

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
        }

        // 兜底：避免越界导致崩溃
        if (adjustedTab < 0 || adjustedTab >= adapter.getCount()) {
            adjustedTab = 0;
        }
        
        defaultTabSpinner.setSelection(adjustedTab);

        // 保存用户选择
        defaultTabSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                SharedPreferences.Editor editor = prefs.edit();
                // 将新索引映射回原索引格式（用于MainActivity的兼容性）
                // 新索引：0=首页, 1=主题, 2=RSS, 3=随机
                // 原索引：0=首页, 2=主题, 3=RSS（跳过已废弃的1=标签）, 4=随机
                int savedTab;
                if (position == 0) {
                    savedTab = 0;
                } else if (position == 1) {
                    savedTab = 2;
                } else if (position == 2) {
                    savedTab = 3;
                } else {
                    savedTab = 4; // 随机
                }
                editor.putInt("default_tab", savedTab);
                editor.apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 初始化最近标签窗口大小 Spinner
        recentTagsWindowSpinner = root.findViewById(R.id.recent_tags_window_spinner);
        ArrayAdapter<CharSequence> windowAdapter = ArrayAdapter.createFromResource(getContext(),
                R.array.recent_tags_window_array, android.R.layout.simple_spinner_item);
        windowAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        recentTagsWindowSpinner.setAdapter(windowAdapter);

        // 加载保存的窗口大小
        int savedWindow = RecentTagsManager.getRecentTagsWindow(requireContext());
        // 将保存的值转换为 Spinner 索引 (保存值-3 = 索引)
        int windowIndex = Math.max(0, Math.min(7, savedWindow - 3));
        recentTagsWindowSpinner.setSelection(windowIndex);

        // 保存用户选择
        recentTagsWindowSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int window = position + 3;  // 索引+3 = 实际窗口值
                RecentTagsManager.setRecentTagsWindow(requireContext(), window);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 初始化服务器URL输入框
        serverUrlInput = root.findViewById(R.id.server_url_input);
        
        // 从 SharedPreferences 加载保存的URL
        // use named prefs for cross-component access
        SharedPreferences globalPrefs = requireActivity().getSharedPreferences("settings", Context.MODE_PRIVATE);
        String savedUrl = globalPrefs.getString("server_url", DEFAULT_SERVER_URL);
        serverUrlInput.setText(savedUrl);

        // 监听输入框内容变化
        serverUrlInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String newUrl = serverUrlInput.getText().toString().trim();
                if (newUrl.isEmpty()) {
                    newUrl = DEFAULT_SERVER_URL;
                    serverUrlInput.setText(newUrl);
                }
                // 保存新的URL到全局设置
                SharedPreferences.Editor editor = globalPrefs.edit();
                editor.putString("server_url", newUrl);
                editor.apply();
            }
        });

        // 初始化同步密钥输入框
        syncSecretKeyInput = root.findViewById(R.id.sync_secret_key_input);
        syncStatusText = root.findViewById(R.id.sync_status_text);

        // 加载保存的密钥
        String savedKey = syncManager.getSecretKey();
        syncSecretKeyInput.setText(savedKey);

        // 监听密钥输入框焦点变化
        syncSecretKeyInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String newKey = syncSecretKeyInput.getText().toString().trim();
                String serverUrl = serverUrlInput.getText().toString().trim();
                if (!newKey.isEmpty() && !serverUrl.isEmpty()) {
                    syncManager.saveConfig(serverUrl, newKey);
                }
            }
        });

        // 同步按钮点击事件
        root.findViewById(R.id.button_sync).setOnClickListener(v -> {
            // 先保存当前配置
            String serverUrl = serverUrlInput.getText().toString().trim();
            String secretKey = syncSecretKeyInput.getText().toString().trim();

            if (serverUrl.isEmpty() || secretKey.isEmpty()) {
                syncStatusText.setText("请填写服务器地址和同步密钥");
                return;
            }

            syncManager.saveConfig(serverUrl, secretKey);
            performSync();
        });

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
                Snackbar.make(requireView(), "已保存：保留 " + count + " 条历史", Snackbar.LENGTH_SHORT).show();
            }
        });

        return root;
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
        syncStatusText.setText("正在同步...");

        new Thread(() -> {
            SimpleSyncManager.SyncResult result = syncManager.sync();

            requireActivity().runOnUiThread(() -> {
                if (result.success) {
                    String status = String.format("同步成功: 上传 %d 条, 下载 %d 条",
                        result.uploadedCount, result.downloadedCount);
                    syncStatusText.setText(status);
                } else {
                    syncStatusText.setText("同步失败: " + result.message);
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
        startActivityForResult(Intent.createChooser(intent, "选择 CSV 或 JSON 文件"), REQUEST_CODE_IMPORT_FILE);
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
        checkBox.setText("去重（跳过已存在的链接）");
        checkBox.setChecked(true); // 默认勾选去重
        
        // 创建布局容器
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        layout.addView(checkBox);
        
        new AlertDialog.Builder(requireContext())
            .setTitle("导入选项")
            .setView(layout)
            .setPositiveButton("确定", (dialog, which) -> {
                boolean removeDuplicates = checkBox.isChecked();
                performImport(removeDuplicates);
            })
            .setNegativeButton("取消", null)
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
            Snackbar.make(requireView(), "没有可导入的链接", Snackbar.LENGTH_SHORT).show();
            return;
        }
        
        // 如果启用去重，过滤掉已存在的链接
        List<LinkItem> itemsToImport = result.items;
        int duplicateCount = 0;
        
        if (removeDuplicates) {
            List<LinkItem> filteredItems = new ArrayList<>();
            for (LinkItem item : itemsToImport) {
                if (!linkDao.urlExists(item.getUrl())) {
                    filteredItems.add(item);
                } else {
                    duplicateCount++;
                }
            }
            itemsToImport = filteredItems;
        }
        
        // 将导入的链接保存到数据库
        int importedCount = 0;
        for (LinkItem item : itemsToImport) {
            try {
                linkDao.insertLink(item);
                importedCount++;
            } catch (Exception e) {
                // 忽略插入失败的情况
            }
        }
        
        // 显示导入结果
        StringBuilder message = new StringBuilder();
        message.append(result.format).append(" 导入完成");
        
        if (importedCount > 0) {
            message.append("，成功导入 ").append(importedCount).append(" 条");
        }
        
        if (removeDuplicates && duplicateCount > 0) {
            message.append("，跳过 ").append(duplicateCount).append(" 条重复链接");
        }
        
        if (result.errorCount > 0) {
            message.append("，失败 ").append(result.errorCount).append(" 条");
        }
        
        Snackbar.make(requireView(), message.toString(), Snackbar.LENGTH_LONG).show();
        
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
            .setTitle("选择导出方式")
            .setView(dialogView)
            .setPositiveButton("确定", (d, which) -> {
                boolean isJson = (formatGroup.getCheckedRadioButtonId() == jsonRadio.getId());
                boolean isSave = (actionGroup.getCheckedRadioButtonId() == saveRadio.getId());
                
                if (isSave) {
                    exportAndSave(isJson);
                } else {
                    exportAndShare(isJson);
                }
            })
            .setNegativeButton("取消", null)
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
                fileUri = ExportUtil.exportToPublicDirectory(
                    requireContext(), 
                    linkDao.getAllLinks(), 
                    isJson
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
                fileUri = ExportUtil.exportToPublicDirectory(
                    requireContext(), 
                    linkDao.getAllLinks(), 
                    isJson
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
    
}