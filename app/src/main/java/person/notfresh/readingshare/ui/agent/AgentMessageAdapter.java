package person.notfresh.readingshare.ui.agent;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import person.notfresh.readingshare.R;

/**
 * agent 对话列表的 Adapter。
 *
 * <p>一条布局两种角色(user / agent / error),靠 visibility 切换 ——
 * 不写两套布局,见 item_agent_message.xml 的注释。</p>
 */
public class AgentMessageAdapter extends RecyclerView.Adapter<AgentMessageAdapter.MessageViewHolder> {

    /** 一条消息。 */
    public static final class Message {
        public static final int ROLE_USER = 0;
        public static final int ROLE_AGENT = 1;
        public static final int ROLE_ERROR = 2;
        /** 工具调用流水 —— 让 agent 的动作可见。 */
        public static final int ROLE_TOOL = 3;

        public final int role;
        public final String text;

        public Message(int role, String text) {
            this.role = role;
            this.text = text;
        }
    }

    private final List<Message> messages = new ArrayList<>();

    public void addMessage(int role, String text) {
        messages.add(new Message(role, text));
        notifyItemInserted(messages.size() - 1);
    }

    public void clear() {
        messages.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_agent_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message msg = messages.get(position);
        holder.bind(msg);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    /** ViewHolder —— 四个 TextView 按角色切换显示。 */
    static class MessageViewHolder extends RecyclerView.ViewHolder {
        private final TextView userView;
        private final TextView agentView;
        private final TextView errorView;
        private final TextView toolView;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            userView = itemView.findViewById(R.id.msg_user);
            agentView = itemView.findViewById(R.id.msg_agent);
            errorView = itemView.findViewById(R.id.msg_error);
            toolView = itemView.findViewById(R.id.msg_tool);
        }

        void bind(Message msg) {
            userView.setVisibility(msg.role == Message.ROLE_USER ? View.VISIBLE : View.GONE);
            agentView.setVisibility(msg.role == Message.ROLE_AGENT ? View.VISIBLE : View.GONE);
            errorView.setVisibility(msg.role == Message.ROLE_ERROR ? View.VISIBLE : View.GONE);
            toolView.setVisibility(msg.role == Message.ROLE_TOOL ? View.VISIBLE : View.GONE);

            String text = msg.text == null ? "" : msg.text;
            switch (msg.role) {
                case Message.ROLE_USER:
                    userView.setText(text);
                    break;
                case Message.ROLE_AGENT:
                    agentView.setText(text);
                    break;
                case Message.ROLE_TOOL:
                    toolView.setText(text);
                    break;
                default:
                    errorView.setText(text);
                    break;
            }
        }
    }
}
