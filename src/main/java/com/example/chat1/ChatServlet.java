package com.example.chat1;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@WebServlet("/chat/*")
public class ChatServlet extends HttpServlet {

    private static final List<String> onlineUsers = new CopyOnWriteArrayList<>();
    private static final List<ChatMessage> messages = new CopyOnWriteArrayList<>();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String pathInfo = request.getPathInfo();
        System.out.println("POST请求路径: " + pathInfo);

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();

        try {
            if (pathInfo == null) {
                sendError(out, "路径不能为空");
                return;
            }

            switch (pathInfo) {
                case "/login":
                    handleLogin(request, out);
                    break;
                case "/send":
                    handleSendMessage(request, out);
                    break;
                case "/logout":
                    handleLogout(request, out);
                    break;
                default:
                    sendError(out, "无效的请求路径: " + pathInfo);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(out, "服务器错误: " + e.getMessage());
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String pathInfo = request.getPathInfo();
        System.out.println("GET请求路径: " + pathInfo);

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();

        try {
            if (pathInfo == null || !"/messages".equals(pathInfo)) {
                sendError(out, "无效的请求路径");
                return;
            }

            handleGetMessages(request, out);
        } catch (Exception e) {
            e.printStackTrace();
            sendError(out, "服务器错误: " + e.getMessage());
        }
    }

    private void handleLogin(HttpServletRequest request, PrintWriter out) {
        String username = request.getParameter("username");
        System.out.println("登录请求，用户名: " + username);

        if (username == null || username.trim().isEmpty()) {
            sendError(out, "用户名不能为空");
            return;
        }

        username = username.trim();

        synchronized (onlineUsers) {
            if (onlineUsers.contains(username)) {
                sendError(out, "用户名已存在，请选择其他用户名");
                return;
            }

            onlineUsers.add(username);
            HttpSession session = request.getSession();
            session.setAttribute("username", username);

            addSystemMessage(username + " 加入了聊天室");

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("username", username);
            result.put("message", "登录成功");

            out.print(toJson(result));
        }
    }

    private void handleSendMessage(HttpServletRequest request, PrintWriter out) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            sendError(out, "请先登录");
            return;
        }

        String username = (String) session.getAttribute("username");
        if (username == null) {
            sendError(out, "用户未登录");
            return;
        }

        String content = request.getParameter("content");
        if (content == null || content.trim().isEmpty()) {
            sendError(out, "消息内容不能为空");
            return;
        }

        content = content.trim();
        messages.add(new ChatMessage(username, content, System.currentTimeMillis()));

        if (messages.size() > 1000) {
            messages.remove(0);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        out.print(toJson(result));
    }

    private void handleGetMessages(HttpServletRequest request, PrintWriter out) {
        String lastTimeStr = request.getParameter("lastTime");
        long lastTime = 0;

        try {
            if (lastTimeStr != null) {
                lastTime = Long.parseLong(lastTimeStr);
            }
        } catch (NumberFormatException e) {
            // 使用默认值0
        }

        List<Map<String, Object>> newMessages = new ArrayList<>();
        for (ChatMessage msg : messages) {
            if (msg.timestamp > lastTime) {
                Map<String, Object> messageMap = new HashMap<>();
                messageMap.put("sender", msg.sender);
                messageMap.put("content", msg.content);
                messageMap.put("timestamp", msg.timestamp);
                messageMap.put("type", msg.type);
                newMessages.add(messageMap);
            }
        }

        long currentLastTime = lastTime;
        if (!messages.isEmpty()) {
            currentLastTime = messages.get(messages.size() - 1).timestamp;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("messages", newMessages);
        result.put("lastTime", currentLastTime);
        result.put("users", new ArrayList<>(onlineUsers));

        out.print(toJson(result));
    }

    private void handleLogout(HttpServletRequest request, PrintWriter out) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            String username = (String) session.getAttribute("username");
            if (username != null) {
                onlineUsers.remove(username);
                addSystemMessage(username + " 离开了聊天室");
            }
            session.invalidate();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        out.print(toJson(result));
    }

    private void addSystemMessage(String content) {
        ChatMessage systemMsg = new ChatMessage("system", content, System.currentTimeMillis());
        systemMsg.type = "system";
        messages.add(systemMsg);

        if (messages.size() > 1000) {
            messages.remove(0);
        }
    }

    private void sendError(PrintWriter out, String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", message);
        out.print(toJson(result));
    }

    private String toJson(Map<String, Object> data) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        boolean first = true;

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!first) json.append(",");
            first = false;

            json.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();

            if (value instanceof String) {
                json.append("\"").append(escapeJson((String) value)).append("\"");
            } else if (value instanceof Number || value instanceof Boolean) {
                json.append(value);
            } else if (value instanceof List) {
                json.append(listToJson((List<?>) value));
            } else {
                json.append("\"").append(value).append("\"");
            }
        }

        json.append("}");
        return json.toString();
    }

    private String listToJson(List<?> list) {
        StringBuilder json = new StringBuilder();
        json.append("[");
        boolean first = true;

        for (Object item : list) {
            if (!first) json.append(",");
            first = false;

            if (item instanceof String) {
                json.append("\"").append(escapeJson((String) item)).append("\"");
            } else if (item instanceof Map) {
                json.append(toJson((Map<String, Object>) item));
            } else {
                json.append("\"").append(item).append("\"");
            }
        }

        json.append("]");
        return json.toString();
    }

    private String escapeJson(String str) {
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static class ChatMessage {
        String sender;
        String content;
        long timestamp;
        String type = "user";

        ChatMessage(String sender, String content, long timestamp) {
            this.sender = sender;
            this.content = content;
            this.timestamp = timestamp;
        }
    }
}