package person.notfresh.readingshare.ui.document;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
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

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.db.DocumentDao;
import person.notfresh.readingshare.model.BookmarkItem;
import person.notfresh.readingshare.model.DocumentItem;
import person.notfresh.readingshare.model.DocumentType;
import person.notfresh.readingshare.util.PdfOutlineExtractor;

public class DocumentViewerActivity extends AppCompatActivity {
    private static final String TAG = "DocumentViewerActivity";
    
    private ImageView pdfImageView;
    private Button btnPrev;
    private Button btnNext;
    private Button btnToc;
    private Button btnCloseToc;
    private Button btnViewPages;
    private Button btnViewOutline;
    private Button btnViewBookmarks;
    private TextView tvPageInfo;
    
    private DocumentDao documentDao;
    private DocumentItem document;
    private File pdfFile;
    
    // PDF渲染相关
    private ParcelFileDescriptor fileDescriptor;
    private PdfRenderer pdfRenderer;
    private PdfRenderer.Page currentPage;
    private int currentPageIndex = 0;
    private int totalPages = 0;
    
    // 目录相关
    private DrawerLayout drawerLayout;
    private RecyclerView recyclerViewToc;
    private TocAdapter tocAdapter;
    private List<TocOutlineItem> outlineItems = new ArrayList<>();
    
    // 保存相关
    private boolean isExternalOpen = false; // 是否是从外部打开
    private boolean isSavedToDocuments = false; // 是否已保存到文档列表
    private Uri originalUri; // 原始URI（用于外部打开的文件）
    private boolean isLandscape = false; // 当前是否为横屏
    private boolean bookmarkExistsForCurrentPage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_document_viewer);

        // 设置 Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(android.R.drawable.ic_menu_revert);
        }
        toolbar.setNavigationOnClickListener(v -> {
            if (isExternalOpen) {
                // 从外部打开，返回应用主界面并导航到文档列表
                Intent intent = new Intent(this, person.notfresh.readingshare.MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                intent.putExtra("navigate_to", "documents");
                startActivity(intent);
                finish();
            } else {
                // 从应用内部打开，正常返回
                finish();
            }
        });

        // 初始化视图
        pdfImageView = findViewById(R.id.pdf_image_view);
        btnPrev = findViewById(R.id.btn_prev);
        btnNext = findViewById(R.id.btn_next);
        btnToc = findViewById(R.id.btn_toc);
        btnCloseToc = findViewById(R.id.btn_close_toc);
        btnViewPages = findViewById(R.id.btn_view_pages);
        btnViewOutline = findViewById(R.id.btn_view_outline);
        btnViewBookmarks = findViewById(R.id.btn_view_bookmarks);
        tvPageInfo = findViewById(R.id.tv_page_info);
        drawerLayout = findViewById(R.id.drawer_layout);
        recyclerViewToc = findViewById(R.id.recycler_view_toc);

        // 初始化数据库
        documentDao = new DocumentDao(this);
        documentDao.open();

        // 处理Intent
        Intent intent = getIntent();
        String action = intent.getAction();
        Uri data = intent.getData();

        // 确保有URI权限（对于content:// URI）
        if (data != null && "content".equals(data.getScheme())) {
            try {
                getContentResolver().takePersistableUriPermission(
                        data, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException e) {
                Log.w(TAG, "无法获取URI持久权限", e);
            }
        }

        if (Intent.ACTION_VIEW.equals(action) && data != null) {
            // 从外部应用打开PDF文件
            isExternalOpen = true;
            originalUri = data;
            handleExternalPdf(data);
        } else if (Intent.ACTION_SEND.equals(action) && data != null) {
            // 分享的PDF文件
            isExternalOpen = true;
            originalUri = data;
            handleSharedPdf(data);
        } else {
            // 从应用内部打开（通过document_id）
            isExternalOpen = false;
            handleInternalPdf(intent);
        }

        // 设置按钮点击事件
        btnPrev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPreviousPage();
            }
        });

        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showNextPage();
            }
        });

        btnToc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openTocDrawer();
            }
        });

        btnCloseToc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeTocDrawer();
            }
        });

        btnViewPages.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchToPageView();
            }
        });

        btnViewOutline.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchToOutlineView();
            }
        });

        btnViewBookmarks.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchToBookmarkView();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.document_viewer_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem saveItem = menu.findItem(R.id.action_save_to_documents);
        if (saveItem != null) {
            // 只有从外部打开且未保存时才显示保存按钮
            saveItem.setVisible(isExternalOpen && !isSavedToDocuments && pdfFile != null);
        }
        MenuItem bookmarkItem = menu.findItem(R.id.action_add_bookmark);
        if (bookmarkItem != null) {
            if (document != null && document.getId() > 0) {
                List<BookmarkItem> bookmarks = documentDao.getBookmarksByDocument(document.getId());
                bookmarkExistsForCurrentPage = false;
                for (BookmarkItem bm : bookmarks) {
                    if (bm.getPageIndex() == currentPageIndex) {
                        bookmarkExistsForCurrentPage = true;
                        bookmarkItem.setTitle("编辑书签");
                        break;
                    }
                }
                if (!bookmarkExistsForCurrentPage) {
                    bookmarkItem.setTitle("添加书签");
                }
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_add_bookmark) {
            showBookmarkDialog();
            return true;
        } else if (id == R.id.action_save_to_documents) {
            saveToDocuments();
            return true;
        } else if (id == R.id.action_toggle_orientation) {
            // 切换横竖屏
            if (isLandscape) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            }
            isLandscape = !isLandscape;
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * 处理从应用内部打开的PDF（通过document_id）
     */
    private void handleInternalPdf(Intent intent) {
        long documentId = intent.getLongExtra("document_id", -1);
        if (documentId == -1) {
            Toast.makeText(this, "文档ID无效", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        document = documentDao.getDocumentById(documentId);
        if (document == null) {
            Toast.makeText(this, "文档不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 更新打开次数
        document.incrementClickCount();
        documentDao.updateClickCount(document.getId(), document.getClickCount());

        // 设置标题
        setTitle(document.getTitle());

        // 加载PDF
        pdfFile = new File(document.getFilePath());
        // 从内部打开，肯定已保存
        isSavedToDocuments = true;
        loadPdf();
    }

    /**
     * 处理从外部应用打开的PDF（通过URI）
     */
    private void handleExternalPdf(Uri uri) {
        Log.d(TAG, "处理外部PDF: " + uri.toString());

        try {
            // 获取文件名
            String fileName = getFileName(uri);
            if (fileName == null || fileName.isEmpty()) {
                fileName = "external_" + System.currentTimeMillis() + ".pdf";
            }

            // 复制文件到应用私有目录
            String destPath = copyFileToPrivateDir(uri, fileName);
            if (destPath == null) {
                // 如果复制失败，尝试直接打开外部文件
                pdfFile = getFileFromUri(uri);
                if (pdfFile == null || !pdfFile.exists()) {
                    Toast.makeText(this, "无法打开PDF文件", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                // 直接打开外部文件，未保存到文档列表
                isSavedToDocuments = false;
                setTitle(fileName);
                loadPdf();
                invalidateOptionsMenu(); // 刷新菜单，显示保存按钮
                return;
            }

            // 检查是否已存在（避免重复导入）
            File destFile = new File(destPath);
            pdfFile = destFile;

            // 检查文档是否已在数据库中
            DocumentItem existingDoc = findExistingDocument(destPath);
            if (existingDoc != null) {
                // 已存在，更新打开次数
                document = existingDoc;
                document.incrementClickCount();
                documentDao.updateClickCount(document.getId(), document.getClickCount());
                setTitle(document.getTitle());
                // 已保存到文档列表
                isSavedToDocuments = true;
            } else {
                // 不存在，未保存到文档列表，显示保存按钮
                setTitle(fileName);
                isSavedToDocuments = false;
            }

            loadPdf();
            invalidateOptionsMenu(); // 刷新菜单

        } catch (Exception e) {
            Log.e(TAG, "处理外部PDF失败", e);
            Toast.makeText(this, "打开PDF失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    /**
     * 处理分享的PDF文件
     */
    private void handleSharedPdf(Uri uri) {
        handleExternalPdf(uri);
    }

    /**
     * 从URI获取文件名
     */
    private String getFileName(Uri uri) {
        String fileName = null;
        String scheme = uri.getScheme();
        
        if ("content".equals(scheme)) {
            android.database.Cursor cursor = getContentResolver().query(
                    uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex);
                }
                cursor.close();
            }
        } else if ("file".equals(scheme)) {
            String path = uri.getPath();
            if (path != null) {
                int cut = path.lastIndexOf('/');
                if (cut != -1) {
                    fileName = path.substring(cut + 1);
                } else {
                    fileName = path;
                }
            }
        }
        
        return fileName;
    }

    /**
     * 从URI获取File对象（如果是file:// URI）
     */
    private File getFileFromUri(Uri uri) {
        if ("file".equals(uri.getScheme())) {
            String path = uri.getPath();
            if (path != null) {
                return new File(path);
            }
        }
        return null;
    }

    /**
     * 复制文件到应用私有目录
     */
    private String copyFileToPrivateDir(Uri uri, String fileName) {
        try {
            File documentsDir = new File(getFilesDir(), "documents");
            File pdfDir = new File(documentsDir, "pdf");
            if (!pdfDir.exists()) {
                pdfDir.mkdirs();
            }

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(new Date());
            String destFileName = timestamp + "_" + fileName;
            File destFile = new File(pdfDir, destFileName);

            InputStream inputStream = getContentResolver().openInputStream(uri);
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
            Log.e(TAG, "复制文件失败", e);
            return null;
        }
    }

    /**
     * 查找已存在的文档记录（不创建新记录）
     */
    private DocumentItem findExistingDocument(String filePath) {
        java.util.List<DocumentItem> allDocs = documentDao.getAllDocuments();
        for (DocumentItem doc : allDocs) {
            if (doc.getFilePath().equals(filePath)) {
                return doc;
            }
        }
        return null;
    }

    /**
     * 查找或创建文档记录
     */
    private DocumentItem findOrCreateDocument(String filePath, String fileName) {
        // 查找是否已存在
        DocumentItem existing = findExistingDocument(filePath);
        if (existing != null) {
            return existing;
        }

        // 不存在则创建新记录
        File file = new File(filePath);
        DocumentItem item = new DocumentItem(fileName, filePath, DocumentType.PDF);
        item.setFileSize(file.length());
        long id = documentDao.insertDocument(item);
        item.setId(id);
        return item;
    }

    /**
     * 加载PDF文件
     */
    private void loadPdf() {
        if (pdfFile == null || !pdfFile.exists()) {
            Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        try {
            // 打开PDF文件
            fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
            pdfRenderer = new PdfRenderer(fileDescriptor);
            totalPages = pdfRenderer.getPageCount();
            
            Log.d(TAG, "PDF加载成功，共 " + totalPages + " 页");

            // 显示第一页
            currentPageIndex = 0;
            showPage(currentPageIndex);

            // 初始化目录
            initToc();

        } catch (Exception e) {
            Log.e(TAG, "加载PDF失败", e);
            Toast.makeText(this, "无法加载PDF文件: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    /**
     * 初始化目录
     */
    private void initToc() {
        if (totalPages <= 0) {
            return;
        }

        // 创建页面列表
        List<Integer> pageNumbers = new ArrayList<>();
        for (int i = 1; i <= totalPages; i++) {
            pageNumbers.add(i);
        }

        // 尝试提取PDF目录（如果PDF有内置目录）
        extractPdfOutline();

        // 设置适配器
        tocAdapter = new TocAdapter(pageNumbers, currentPageIndex);
        tocAdapter.setOutlineItems(outlineItems);
        tocAdapter.setOnPageClickListener(new TocAdapter.OnPageClickListener() {
            @Override
            public void onPageClick(int pageIndex) {
                showPage(pageIndex);
                closeTocDrawer();
            }
        });

        tocAdapter.setOnBookmarkDeleteCallback(bookmarkId -> {
            documentDao.deleteBookmark(bookmarkId);
            // 刷新书签列表
            List<BookmarkItem> updated = documentDao.getBookmarksByDocument(document.getId());
            tocAdapter.setBookmarks(updated);
            Toast.makeText(DocumentViewerActivity.this, "书签已删除", Toast.LENGTH_SHORT).show();
            invalidateOptionsMenu();
        });

        // 加载书签数据
        if (document != null && document.getId() > 0) {
            tocAdapter.setBookmarks(documentDao.getBookmarksByDocument(document.getId()));
        }

        recyclerViewToc.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewToc.setAdapter(tocAdapter);

        // 默认显示页码视图
        switchToPageView();
    }

    /**
     * 提取PDF目录/大纲
     * 使用自定义的PDF解析器提取目录信息
     */
    private void extractPdfOutline() {
        outlineItems.clear();
        
        if (pdfFile == null || !pdfFile.exists()) {
            Log.w(TAG, "PDF文件不存在，无法提取目录");
            return;
        }

        try {
            // 使用自定义的PDF目录提取器
            List<TocOutlineItem> extracted = PdfOutlineExtractor.extractOutline(pdfFile);
            outlineItems.addAll(extracted);
            
            Log.d(TAG, "PDF目录提取完成，共 " + outlineItems.size() + " 个目录项");
            
            // 如果提取到目录，打印前几个作为调试信息
            if (!outlineItems.isEmpty()) {
                for (int i = 0; i < Math.min(5, outlineItems.size()); i++) {
                    TocOutlineItem item = outlineItems.get(i);
                    Log.d(TAG, "目录项 " + (i + 1) + ": " + item.getTitle() + " -> 第" + item.getPageNumber() + "页");
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "提取PDF目录失败", e);
        }
    }

    /**
     * 切换到页码视图
     */
    private void switchToPageView() {
        if (tocAdapter != null) {
            tocAdapter.setPageView(true);
            btnViewPages.setEnabled(false);
            btnViewOutline.setEnabled(true);
            btnViewBookmarks.setEnabled(true);
        }
    }

    /**
     * 切换到目录视图
     */
    private void switchToOutlineView() {
        if (tocAdapter != null) {
            tocAdapter.setPageView(false);
            btnViewPages.setEnabled(true);
            btnViewOutline.setEnabled(false);
            btnViewBookmarks.setEnabled(true);
        }
    }

    private void switchToBookmarkView() {
        if (tocAdapter != null) {
            tocAdapter.setViewType(TocAdapter.ViewType.BOOKMARK);
            btnViewPages.setEnabled(true);
            btnViewOutline.setEnabled(true);
            btnViewBookmarks.setEnabled(false);
        }
    }

    /**
     * 打开目录侧边栏
     */
    private void openTocDrawer() {
        if (drawerLayout != null) {
            drawerLayout.openDrawer(findViewById(R.id.drawer_content));
        }
    }

    /**
     * 关闭目录侧边栏
     */
    private void closeTocDrawer() {
        if (drawerLayout != null) {
            drawerLayout.closeDrawer(findViewById(R.id.drawer_content));
        }
    }

    /**
     * 显示指定页面
     */
    private void showPage(int pageIndex) {
        if (pdfRenderer == null || pageIndex < 0 || pageIndex >= totalPages) {
            return;
        }

        try {
            // 关闭当前页面
            if (currentPage != null) {
                currentPage.close();
            }

            // 打开新页面
            currentPage = pdfRenderer.openPage(pageIndex);
            currentPageIndex = pageIndex;

            // 计算缩放比例以适应屏幕宽度
            int width = getResources().getDisplayMetrics().widthPixels;
            int height = (int) (width * 1.414f); // A4比例，可以根据需要调整
            
            // 渲染页面为Bitmap
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(android.graphics.Color.WHITE);
            currentPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

            // 显示Bitmap
            pdfImageView.setImageBitmap(bitmap);

            // 更新页面信息
            updatePageInfo();

            // 更新按钮状态
            btnPrev.setEnabled(pageIndex > 0);
            btnNext.setEnabled(pageIndex < totalPages - 1);

            // 更新目录高亮
            if (tocAdapter != null) {
                tocAdapter.setCurrentPage(pageIndex);
            }

            // 刷新菜单以更新书签按钮文字
            invalidateOptionsMenu();

        } catch (Exception e) {
            Log.e(TAG, "显示页面失败", e);
            Toast.makeText(this, "显示页面失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 显示上一页
     */
    private void showPreviousPage() {
        if (currentPageIndex > 0) {
            showPage(currentPageIndex - 1);
        }
    }

    /**
     * 显示下一页
     */
    private void showNextPage() {
        if (currentPageIndex < totalPages - 1) {
            showPage(currentPageIndex + 1);
        }
    }

    /**
     * 更新页面信息显示
     */
    private void updatePageInfo() {
        if (tvPageInfo != null) {
            tvPageInfo.setText(String.format(Locale.getDefault(), "%d / %d", 
                    currentPageIndex + 1, totalPages));
        }
        
        // 更新标题
        if (document != null) {
            setTitle(document.getTitle() + " (" + (currentPageIndex + 1) + "/" + totalPages + ")");
        }
    }

    /**
     * 保存到文档列表
     */
    private void saveToDocuments() {
        if (pdfFile == null || !pdfFile.exists()) {
            Toast.makeText(this, "文件不存在，无法保存", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String filePath = pdfFile.getAbsolutePath();
            String fileName = pdfFile.getName();
            
            // 如果文件不在应用私有目录，需要先复制
            if (!filePath.contains(getFilesDir().getAbsolutePath())) {
                // 从原始URI复制文件
                if (originalUri != null) {
                    String destPath = copyFileToPrivateDir(originalUri, fileName);
                    if (destPath != null) {
                        filePath = destPath;
                        pdfFile = new File(filePath);
                    } else {
                        Toast.makeText(this, "复制文件失败，无法保存", Toast.LENGTH_SHORT).show();
                        return;
                    }
                } else {
                    // 直接从当前文件复制
                    File documentsDir = new File(getFilesDir(), "documents");
                    File pdfDir = new File(documentsDir, "pdf");
                    if (!pdfDir.exists()) {
                        pdfDir.mkdirs();
                    }

                    String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                            .format(new Date());
                    String destFileName = timestamp + "_" + fileName;
                    File destFile = new File(pdfDir, destFileName);

                    // 复制文件
                    java.io.FileInputStream fis = new java.io.FileInputStream(pdfFile);
                    FileOutputStream fos = new FileOutputStream(destFile);
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                    fis.close();
                    fos.close();

                    filePath = destFile.getAbsolutePath();
                    pdfFile = destFile;
                }
            }

            // 检查是否已存在
            java.util.List<DocumentItem> allDocs = documentDao.getAllDocuments();
            for (DocumentItem doc : allDocs) {
                if (doc.getFilePath().equals(filePath)) {
                    // 已存在，更新打开次数
                    document = doc;
                    document.incrementClickCount();
                    documentDao.updateClickCount(document.getId(), document.getClickCount());
                    isSavedToDocuments = true;
                    invalidateOptionsMenu();
                    Toast.makeText(this, "文档已存在于文档列表中", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            // 创建新文档记录
            DocumentItem item = new DocumentItem(fileName, filePath, DocumentType.PDF);
            item.setFileSize(pdfFile.length());
            long id = documentDao.insertDocument(item);
            item.setId(id);
            document = item;
            isSavedToDocuments = true;
            invalidateOptionsMenu();
            
            Toast.makeText(this, "已保存到文档列表", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "文档已保存: " + filePath);

        } catch (Exception e) {
            Log.e(TAG, "保存文档失败", e);
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 显示书签添加/编辑对话框
     */
    private void showBookmarkDialog() {
        if (document == null || document.getId() <= 0) {
            Toast.makeText(this, "请先将文档保存到文档列表", Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查当前页是否已有书签
        List<BookmarkItem> bookmarks = documentDao.getBookmarksByDocument(document.getId());
        BookmarkItem existingBookmark = null;
        for (BookmarkItem bm : bookmarks) {
            if (bm.getPageIndex() == currentPageIndex) {
                existingBookmark = bm;
                break;
            }
        }

        boolean isEdit = existingBookmark != null;
        final BookmarkItem bookmarkToDelete = existingBookmark;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(isEdit ? "编辑书签" : "添加书签");

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_bookmark, null);
        final EditText etNote = dialogView.findViewById(R.id.et_bookmark_note);
        TextView tvHint = dialogView.findViewById(R.id.tv_bookmark_hint);
        tvHint.setText("当前第 " + (currentPageIndex + 1) + " 页，是否为书签添加备注？");
        if (isEdit && existingBookmark.getNote() != null) {
            etNote.setText(existingBookmark.getNote());
        }
        builder.setView(dialogView);

        builder.setPositiveButton("确定", (dialog, which) -> {
            String note = etNote.getText().toString().trim();
            if (note.isEmpty()) {
                note = null;
            }
            if (isEdit) {
                // 更新备注：先删除旧书签，再添加新书签
                documentDao.deleteBookmark(bookmarkToDelete.getId());
            }
            long result = documentDao.addBookmark(document.getId(), currentPageIndex, note);
            if (result > 0) {
                Toast.makeText(this, isEdit ? "书签已更新" : "已添加书签", Toast.LENGTH_SHORT).show();
                invalidateOptionsMenu();
            } else {
                Toast.makeText(this, "添加书签失败", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("取消", null);

        if (isEdit) {
            builder.setNeutralButton("删除", (dialog, which) -> {
                documentDao.deleteBookmark(bookmarkToDelete.getId());
                Toast.makeText(this, "书签已删除", Toast.LENGTH_SHORT).show();
                invalidateOptionsMenu();
            });
        }

        builder.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // 释放资源
        if (currentPage != null) {
            currentPage.close();
            currentPage = null;
        }
        
        if (pdfRenderer != null) {
            pdfRenderer.close();
            pdfRenderer = null;
        }
        
        if (fileDescriptor != null) {
            try {
                fileDescriptor.close();
            } catch (Exception e) {
                Log.e(TAG, "关闭文件描述符失败", e);
            }
            fileDescriptor = null;
        }
        
        if (documentDao != null) {
            documentDao.close();
        }
    }
}
