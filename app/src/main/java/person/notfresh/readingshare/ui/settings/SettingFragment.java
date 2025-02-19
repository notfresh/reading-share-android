package person.notfresh.readingshare.ui.settings;

import static android.app.Activity.RESULT_OK;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.app.AlertDialog;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.io.File;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.model.LinkItem;
import person.notfresh.readingshare.db.LinkDao;
import com.google.android.material.snackbar.Snackbar;
import person.notfresh.readingshare.util.ExportUtil;
import com.google.android.material.textfield.TextInputEditText;

public class SettingFragment extends Fragment {

    private static final int REQUEST_CODE_IMPORT_CSV = 1; // 定义常量
    private static final String DEFAULT_SERVER_URL = "https://duxiang.ai";

    private Spinner defaultTabSpinner;
    private LinkDao linkDao; // 声明 LinkDao
    private TextInputEditText serverUrlInput;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_slideshow, container, false);

        // 初始化 LinkDao
        linkDao = new LinkDao(requireContext());
        linkDao.open();

        defaultTabSpinner = root.findViewById(R.id.default_tab_spinner);

        // 设置 Spinner 的选项
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(getContext(),
                R.array.default_tabs_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        defaultTabSpinner.setAdapter(adapter);

        // 加载保存的默认 Tab
        SharedPreferences prefs = requireActivity().getPreferences(Context.MODE_PRIVATE);
        int defaultTab = prefs.getInt("default_tab", 0);
        defaultTabSpinner.setSelection(defaultTab);

        // 保存用户选择
        defaultTabSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                SharedPreferences.Editor editor = prefs.edit();
                editor.putInt("default_tab", position);
                editor.apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 初始化服务器URL输入框
        serverUrlInput = root.findViewById(R.id.server_url_input);
        
        // 从 SharedPreferences 加载保存的URL
        String savedUrl = prefs.getString("server_url", DEFAULT_SERVER_URL);
        serverUrlInput.setText(savedUrl);

        // 监听输入框内容变化
        serverUrlInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String newUrl = serverUrlInput.getText().toString().trim();
                if (newUrl.isEmpty()) {
                    newUrl = DEFAULT_SERVER_URL;
                    serverUrlInput.setText(newUrl);
                }
                // 保存新的URL
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString("server_url", newUrl);
                editor.apply();
            }
        });

        // 添加导入 CSV 按钮
        root.findViewById(R.id.button_import_csv).setOnClickListener(v -> {
            importCsv();  // 直接调用本地的 importCsv 方法
        });

        // 添加导出按钮的点击事件
        root.findViewById(R.id.button_export).setOnClickListener(v -> {
            showExportDialog();
        });

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (linkDao != null) {
            linkDao.close(); // 关闭数据库连接
        }
    }

    // 添加导入 CSV 的方法
    private void importCsv() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("text/csv");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "选择 CSV 文件"), REQUEST_CODE_IMPORT_CSV);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_IMPORT_CSV && resultCode == RESULT_OK) {
            if (data != null) {
                Uri uri = data.getData();
                importCsvFromUri(uri);
            }
        }
    }

    private void importCsvFromUri(Uri uri) {
        try {
            // 检查文件后缀是否为csv
            String path = uri.getPath();
            if (!path.endsWith(".csv")) {
                throw new Exception("文件不是CSV格式");
            }

            InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            Log.d("importCsvFromUri", "Line123");
            reader.readLine(); //
            while ((line = reader.readLine()) != null) {
                Log.d("importCsvFromUri", "read line: " + line);
                try {
                    // 使用正则表达式来处理逗号分隔的问题
                    String[] columns = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                    // 检查列数，允许最少3列（标签可以为空）
                    if (columns.length >= 3) {
                        // 移除双引号
                        String title = columns[0].trim().replaceFirst("^\"|\"$", "");
                        String url = columns[1].trim().replaceFirst("^\"|\"$", "");
                        String dateStr = columns[2].trim().replaceFirst("^\"|\"$", "");
                        Date date = null;
                        String[] dateFormats = {
                            "yyyy-MM-dd HH:mm:ss",
                            "yyyy/MM/dd HH:mm",
                            "yyyy-MM-dd HH:mm", 
                            "yyyy/MM/dd"
                        };
                        
                        for (String format : dateFormats) {
                            try {
                                SimpleDateFormat sdf = new SimpleDateFormat(format);
                                date = sdf.parse(dateStr);
                                break;
                            } catch (ParseException e) {
                                continue;
                            }
                        }
                        
                        if (date == null) {
                            throw new ParseException("无法解析日期: " + dateStr, 0);
                        }
                        long timestamp = date.getTime();
                        
                        // 处理标签列
                        List<String> tags = new ArrayList<>();
                        if (columns.length >= 4) {
                            String tagsString = columns[3].trim();
                            if (!tagsString.isEmpty()) {
                                if (tagsString.startsWith("\"") && tagsString.endsWith("\"")) {
                                    // 如果标签字符串被双引号包围，说明可能包含多个标签
                                    // 去掉首尾的双引号
                                    tagsString = tagsString.substring(1, tagsString.length() - 1);
                                    // 按逗号分割，并处理每个标签
                                    String[] tagArray = tagsString.split(",");
                                    for (String tag : tagArray) {
                                        String cleanTag = tag.trim();
                                        if (!cleanTag.isEmpty()) {
                                            tags.add(cleanTag);
                                        }
                                    }
                                } else {
                                    // 如果没有双引号包围，说明只有一个标签
                                    tags.add(tagsString);
                                }
                            }
                            Log.d("importCsvFromUri", "处理标签: 原始值=" + columns[3] + 
                                  ", 解析结果=" + tags.toString());
                        }
                        
                        LinkItem newLink = new LinkItem(title, url, "imported", "", "");
                        newLink.setTimestamp(timestamp);
                        newLink.setTags(tags);
                        Log.d("importCsvFromUri", "创建新链接: " + newLink.toString() + 
                              ", 标签数量=" + tags.size());
                        linkDao.insertLink(newLink);
                    }
                } catch (Exception e) {
                    Log.e("importCsvFromUri", "处理行时出错: " + line + ", 错误: " + e.getMessage());
                    continue;
                }
            }
            reader.close();
            Snackbar.make(requireView(), "CSV 导入成功", Snackbar.LENGTH_LONG).show();
        } catch (Exception e) {
            Snackbar.make(requireView(), "导入失败：" + e.getMessage(), Snackbar.LENGTH_LONG).show();
        }
    }

    private void showExportDialog() {
        String[] items = {"导出为CSV", "导出为JSON"};
        new AlertDialog.Builder(requireContext())
            .setTitle("选择导出格式")
            .setItems(items, (dialog, which) -> {
                switch (which) {
                    case 0:
                        exportToFile(false); // CSV
                        break;
                    case 1:
                        exportToFile(true);  // JSON
                        break;
                }
            })
            .show();
    }

    private void exportToFile(boolean isJson) {
        try {
            String filePath;
            if (isJson) {
                filePath = ExportUtil.exportToJson(requireContext(), linkDao.getAllLinks());
            } else {
                filePath = ExportUtil.exportToCsv(requireContext(), linkDao.getAllLinks());
            }
            
            File file = new File(filePath);
            Uri uri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(),
                requireContext().getPackageName() + ".provider",
                file);
            
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("*/*");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            startActivity(Intent.createChooser(shareIntent, "分享导出文件"));
            
        } catch (Exception e) {
            Snackbar.make(requireView(), 
                "导出失败：" + e.getMessage(), 
                Snackbar.LENGTH_LONG).show();
        }
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