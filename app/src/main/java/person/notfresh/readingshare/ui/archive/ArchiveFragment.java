package person.notfresh.readingshare.ui.archive;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import person.notfresh.readingshare.R;

public class ArchiveFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_archive, container, false);
        
        // 这里暂时是空白的，后续可以添加归档功能的实现
        TextView textView = root.findViewById(R.id.text_archive);
        textView.setText("归档功能即将上线");
        
        return root;
    }
}
