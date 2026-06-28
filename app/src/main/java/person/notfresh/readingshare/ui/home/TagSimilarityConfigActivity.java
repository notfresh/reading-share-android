package person.notfresh.readingshare.ui.home;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.embedding.TagSimilarityOverrideStore;

/**
 * Config page for custom tag proximity rules.
 * Rule format: a, b, boost
 */
public class TagSimilarityConfigActivity extends AppCompatActivity {

    private EditText inputA;
    private EditText inputB;
    private EditText inputBoost;
    private ListView rulesList;
    private ArrayAdapter<String> listAdapter;

    private final List<TagSimilarityOverrideStore.Rule> rules = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tag_similarity_config);

        inputA = findViewById(R.id.input_tag_a);
        inputB = findViewById(R.id.input_tag_b);
        inputBoost = findViewById(R.id.input_boost);
        rulesList = findViewById(R.id.rules_list);
        Button addRuleButton = findViewById(R.id.button_add_rule);
        Button saveButton = findViewById(R.id.button_save_rules);
        ImageButton backButton = findViewById(R.id.button_back);
        TextView removeHint = findViewById(R.id.text_remove_hint);

        backButton.setOnClickListener(v -> finish());
        addRuleButton.setOnClickListener(v -> addRuleFromInput());
        saveButton.setOnClickListener(v -> saveRules());

        listAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        rulesList.setAdapter(listAdapter);

        rulesList.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < rules.size()) {
                rules.remove(position);
                refreshList();
                Toast.makeText(this, "已删除规则", Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });

        removeHint.setText("长按规则可删除");
        loadRules();
    }

    private void loadRules() {
        rules.clear();
        rules.addAll(TagSimilarityOverrideStore.loadRules(this));
        refreshList();
    }

    private void addRuleFromInput() {
        String a = normalize(inputA.getText().toString());
        String b = normalize(inputB.getText().toString());
        String boostText = normalize(inputBoost.getText().toString());

        if (TextUtils.isEmpty(a) || TextUtils.isEmpty(b) || TextUtils.isEmpty(boostText)) {
            Toast.makeText(this, "请完整填写 a、b、boost", Toast.LENGTH_SHORT).show();
            return;
        }

        float boost;
        try {
            boost = Float.parseFloat(boostText);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "boost 需要是数字", Toast.LENGTH_SHORT).show();
            return;
        }

        if (boost <= 0f) {
            Toast.makeText(this, "boost 必须大于 0", Toast.LENGTH_SHORT).show();
            return;
        }

        if (boost > 1.0f) {
            boost = 1.0f;
        }

        upsertRule(a, b, boost);
        refreshList();

        inputA.setText("");
        inputB.setText("");
        inputBoost.setText("");
        inputA.requestFocus();
    }

    private void upsertRule(String a, String b, float boost) {
        String newKey = TagSimilarityOverrideStore.pairKey(a, b);
        for (int i = 0; i < rules.size(); i++) {
            TagSimilarityOverrideStore.Rule existing = rules.get(i);
            if (TagSimilarityOverrideStore.pairKey(existing.a, existing.b).equals(newKey)) {
                rules.set(i, new TagSimilarityOverrideStore.Rule(a, b, boost));
                Toast.makeText(this, "已更新规则", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        rules.add(new TagSimilarityOverrideStore.Rule(a, b, boost));
        Toast.makeText(this, "已添加规则", Toast.LENGTH_SHORT).show();
    }

    private void saveRules() {
        try {
            TagSimilarityOverrideStore.saveRules(this, rules);
            Toast.makeText(this, "规则已保存", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
        } catch (IOException e) {
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void refreshList() {
        List<String> rows = new ArrayList<>();
        for (TagSimilarityOverrideStore.Rule rule : rules) {
            rows.add(rule.a + " , " + rule.b + " , " + String.format(Locale.US, "%.2f", rule.boost));
        }
        listAdapter.clear();
        listAdapter.addAll(rows);
        listAdapter.notifyDataSetChanged();
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}
