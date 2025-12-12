package person.notfresh.readingshare.ui.document;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.adapter.DocumentAdapter;
import person.notfresh.readingshare.databinding.FragmentDocumentBinding;
import person.notfresh.readingshare.db.DocumentDao;
import person.notfresh.readingshare.model.DocumentItem;
import person.notfresh.readingshare.model.DocumentType;

public class DocumentFragment extends Fragment implements DocumentAdapter.OnDocumentActionListener {
    private static final int REQUEST_CODE_PICK_FILE = 1001;

    private FragmentDocumentBinding binding;
    private DocumentAdapter adapter;
    private DocumentDao documentDao;
    private EditText searchEditText;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.document_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_import_document) {
            importDocument();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                            ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentDocumentBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        documentDao = new DocumentDao(requireContext());
        documentDao.open();

        RecyclerView recyclerView = binding.recyclerView;
        adapter = new DocumentAdapter(requireContext());
        adapter.setOnDocumentActionListener(this);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        // 设置搜索框
        searchEditText = binding.searchEditText;
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                adapter.filter(s.toString());
            }
        });

        // 加载文档列表
        loadDocuments();

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        if (documentDao != null) {
            documentDao.close();
        }
    }

    private void loadDocuments() {
        List<DocumentItem> pinnedDocuments = documentDao.getPinnedDocuments();
        Map<String, List<DocumentItem>> groupedDocuments = documentDao.getDocumentsGroupByDate();

        adapter.setPinnedDocuments(pinnedDocuments);
        adapter.setGroupedDocuments(groupedDocuments);
    }

    private void importDocument() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_CODE_PICK_FILE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_PICK_FILE && resultCode == android.app.Activity.RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri uri = data.getData();
                handleFileImport(uri);
            }
        }
    }

    private void handleFileImport(Uri uri) {
        try {
            // 获取文件名
            String fileName = getFileName(uri);
            if (fileName == null || fileName.isEmpty()) {
                fileName = "document_" + System.currentTimeMillis();
            }

            // 确定文档类型
            DocumentType type = DocumentType.fromFilePath(fileName);

            // 复制文件到应用私有目录
            String destPath = copyFileToPrivateDir(uri, fileName, type);
            if (destPath == null) {
                Toast.makeText(requireContext(), "文件导入失败", Toast.LENGTH_SHORT).show();
                return;
            }

            // 获取文件大小
            File file = new File(destPath);
            long fileSize = file.length();

            // 创建 DocumentItem
            DocumentItem item = new DocumentItem(fileName, destPath, type);
            item.setFileSize(fileSize);

            // 保存到数据库
            long id = documentDao.insertDocument(item);
            item.setId(id);

            Toast.makeText(requireContext(), "文档导入成功", Toast.LENGTH_SHORT).show();

            // 刷新列表
            loadDocuments();

        } catch (Exception e) {
            Log.e("DocumentFragment", "导入文件失败", e);
            Toast.makeText(requireContext(), "导入失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String getFileName(Uri uri) {
        String fileName = null;
        String scheme = uri.getScheme();
        if (scheme != null && scheme.equals("content")) {
            android.database.Cursor cursor = requireContext().getContentResolver().query(
                    uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex);
                }
                cursor.close();
            }
        }
        if (fileName == null) {
            fileName = uri.getPath();
            int cut = fileName.lastIndexOf('/');
            if (cut != -1) {
                fileName = fileName.substring(cut + 1);
            }
        }
        return fileName;
    }

    private String copyFileToPrivateDir(Uri uri, String fileName, DocumentType type) {
        try {
            // 创建文档目录
            File documentsDir = new File(requireContext().getFilesDir(), "documents");
            File typeDir = new File(documentsDir, type.name().toLowerCase());
            if (!typeDir.exists()) {
                typeDir.mkdirs();
            }

            // 生成唯一文件名
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(new Date());
            String extension = type.getExtension();
            String destFileName = timestamp + "_" + fileName;
            File destFile = new File(typeDir, destFileName);

            // 复制文件
            InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
            FileOutputStream outputStream = new FileOutputStream(destFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            inputStream.close();
            outputStream.close();

            return destFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e("DocumentFragment", "复制文件失败", e);
            return null;
        }
    }

    @Override
    public void onDeleteDocument(DocumentItem document) {
        // 显示确认对话框
        new AlertDialog.Builder(requireContext())
                .setTitle("确认删除")
                .setMessage("确定要删除文档 \"" + document.getTitle() + "\" 吗？\n\n删除后无法恢复。")
                .setPositiveButton("删除", (dialog, which) -> {
                    // 执行删除
                    boolean deleted = documentDao.deleteDocument(document.getId());
                    if (deleted) {
                        adapter.removeDocumentItem(document);
                        Toast.makeText(requireContext(), "文档已删除", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "删除失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public void onUpdateDocument(DocumentItem oldDocument, String newTitle) {
        documentDao.updateDocumentTitle(oldDocument.getId(), newTitle);
        loadDocuments();
    }

    @Override
    public void onPinStatusChanged() {
        loadDocuments();
    }
}

