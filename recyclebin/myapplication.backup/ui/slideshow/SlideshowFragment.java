package person.notfresh.readingshare.ui.slideshow;

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
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.MainActivity;
import person.notfresh.readingshare.model.LinkItem;
import person.notfresh.readingshare.db.LinkDao;
import com.google.android.material.snackbar.Snackbar;

public class SlideshowFragment extends Fragment {

    private static final int REQUEST_CODE_IMPORT_CSV = 1; // 定义常量

    private Spinner defaultTabSpinner;
    private LinkDao linkDao; // 声明 LinkDao

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

        // 添加导入 CSV 按钮
        root.findViewById(R.id.button_import_csv).setOnClickListener(v -> {
            importCsv();  // 直接调用本地的 importCsv 方法
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
                Log.d("importCsvFromUri", "read line");
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
                        
                        // 处理标签，如果没有第4列则设为空列表
                        List<String> tags = new ArrayList<>();
                        if (columns.length >= 4) {
                            String tagsString = columns[3].trim().replaceFirst("^\"|\"$", "");
                            if (!tagsString.isEmpty()) {
                                tags = Arrays.asList(tagsString.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"));
                                tags = tags.stream()
                                    .map(String::trim)
                                    .map(tag -> tag.replaceFirst("^\"|\"$", ""))
                                    .collect(Collectors.toList());
                            }
                        }
                        
                        Log.d("importCsvFromUriTag", "TagRaw: " + (columns.length >= 4 ? columns[3] : "无标签") + " tags: " + tags.toString());
                        LinkItem newLink = new LinkItem(title, url, "imported", "", "");
                        newLink.setTimestamp(timestamp);
                        newLink.setTags(tags);
                        Log.d("importCsvFromUri", "newLink: " + newLink.toString());
                        linkDao.insertLink(newLink);
                    }
                } catch (Exception e) {
                    // 遇到错误的行直接跳过
                    Log.d("importCsvFromUriError", "error: " + e.getMessage());
                    continue;
                }
            }
            reader.close();
            Snackbar.make(requireView(), "CSV 导入成功", Snackbar.LENGTH_LONG).show();
        } catch (Exception e) {
            Snackbar.make(requireView(), "导入失败：" + e.getMessage(), Snackbar.LENGTH_LONG).show();
        }
    }
}