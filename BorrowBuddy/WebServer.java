import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public class WebServer {
    private final int port;
    private final LendingManager manager;
    private final Path staticDir;
    private HttpServer server;

    public WebServer(int port, LendingManager manager, Path staticDir) {
        this.port = port;
        this.manager = manager;
        this.staticDir = staticDir;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newCachedThreadPool());

        // API routes
        server.createContext("/api/state", new StateHandler());
        server.createContext("/api/users", new UsersHandler());
        server.createContext("/api/items", new ItemsHandler());
        server.createContext("/api/request", new RequestHandler());
        server.createContext("/api/request/approve", new ApproveHandler());
        server.createContext("/api/request/reject", new RejectHandler());
        server.createContext("/api/loan/return", new ReturnHandler());
        server.createContext("/api/loan/report", new ReportHandler());
        server.createContext("/api/user/score", new ScoreHandler());

        // Static file handler (serves index.html, style.css, script.js etc.)
        server.createContext("/", new StaticFileHandler());

        server.start();
        System.out.println("BorrowBuddy Web Server running at http://localhost:" + port);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    // ========================================================= HTTP Handlers

    private class StateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            String json = buildStateJson();
            sendJson(exchange, 200, json);
        }
    }

    private class UsersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                StringBuilder sb = new StringBuilder("[");
                ArrayList<User> users = manager.getAllUsers();
                for (int i = 0; i < users.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(userToJson(users.get(i)));
                }
                sb.append("]");
                sendJson(exchange, 200, sb.toString());
                return;
            } else if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                Map<String, String> body = parseJsonBody(readBody(exchange));
                String name = body.get("name");
                String room = body.get("room");
                if (name == null || name.trim().isEmpty()) {
                    sendJson(exchange, 400, "{\"error\":\"User name is required\"}");
                    return;
                }
                try {
                    User user = manager.registerUser(name.trim(), room != null ? room.trim() : "");
                    sendJson(exchange, 201, userToJson(user));
                } catch (InvalidLoanException e) {
                    sendJson(exchange, 400, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
                }
                return;
            }
            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
        }
    }

    private class ItemsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                Map<String, String> body = parseJsonBody(readBody(exchange));
                String name = body.get("name");
                String category = body.get("category");
                String ownerIdStr = body.get("ownerId");
                String author = body.get("author");
                String subject = body.get("subject");

                if (name == null || name.trim().isEmpty() || ownerIdStr == null) {
                    sendJson(exchange, 400, "{\"error\":\"Missing required item fields\"}");
                    return;
                }

                try {
                    int ownerId = Integer.parseInt(ownerIdStr.trim());
                    User owner = manager.findUser(ownerId);
                    Item item;
                    if (category != null && category.equalsIgnoreCase("Textbook")) {
                        item = new TextBook(name.trim(), owner, author != null ? author.trim() : "Unknown", subject != null ? subject.trim() : "General");
                    } else if (category != null && (category.equalsIgnoreCase("Book") || category.equalsIgnoreCase("Books"))) {
                        item = new Book(name.trim(), owner, author != null ? author.trim() : "Unknown");
                    } else {
                        item = new Item(name.trim(), category != null ? category.trim() : "Other", owner);
                    }
                    manager.addItem(item);
                    sendJson(exchange, 201, itemToJson(item));
                } catch (NumberFormatException e) {
                    sendJson(exchange, 400, "{\"error\":\"Invalid owner ID\"}");
                } catch (InvalidLoanException e) {
                    sendJson(exchange, 400, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
                }
                return;
            }
            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
        }
    }

    private class RequestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            Map<String, String> body = parseJsonBody(readBody(exchange));
            String userIdStr = body.get("userId");
            String itemIdStr = body.get("itemId");
            String daysStr = body.get("days");

            try {
                int userId = Integer.parseInt(userIdStr.trim());
                int itemId = Integer.parseInt(itemIdStr.trim());
                int days = Integer.parseInt(daysStr.trim());

                BorrowRequest req = manager.sendRequest(userId, itemId, days);
                sendJson(exchange, 200, "{\"success\":true,\"requestId\":" + req.getRequestId() + "}");
            } catch (NumberFormatException e) {
                sendJson(exchange, 400, "{\"error\":\"Invalid numerical parameters\"}");
            } catch (InvalidLoanException e) {
                sendJson(exchange, 400, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        }
    }

    private class ApproveHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            Map<String, String> body = parseJsonBody(readBody(exchange));
            try {
                int requestId = Integer.parseInt(body.get("requestId").trim());
                int ownerId = Integer.parseInt(body.get("ownerId").trim());

                Loan loan = manager.approveRequest(requestId, ownerId);
                sendJson(exchange, 200, "{\"success\":true,\"loanId\":" + loan.getLoanId() + ",\"due\":\"" + loan.getDueDate() + "\"}");
            } catch (Exception e) {
                sendJson(exchange, 400, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        }
    }

    private class RejectHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            Map<String, String> body = parseJsonBody(readBody(exchange));
            try {
                int requestId = Integer.parseInt(body.get("requestId").trim());
                int ownerId = Integer.parseInt(body.get("ownerId").trim());

                manager.rejectRequest(requestId, ownerId);
                sendJson(exchange, 200, "{\"success\":true}");
            } catch (Exception e) {
                sendJson(exchange, 400, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        }
    }

    private class ReturnHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            Map<String, String> body = parseJsonBody(readBody(exchange));
            try {
                int loanId = Integer.parseInt(body.get("loanId").trim());
                int userId = Integer.parseInt(body.get("userId").trim());

                Loan loan = manager.returnItem(loanId, userId);
                boolean wasLate = loan.wasReturnedLate();
                sendJson(exchange, 200, "{\"success\":true,\"wasLate\":" + wasLate + "}");
            } catch (Exception e) {
                sendJson(exchange, 400, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        }
    }

    private class ReportHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            Map<String, String> body = parseJsonBody(readBody(exchange));
            try {
                int loanId = Integer.parseInt(body.get("loanId").trim());
                int userId = Integer.parseInt(body.get("userId").trim());
                String type = body.get("type"); // "lost" or "damaged"
                String reason = body.get("reason");
                if (reason == null || reason.trim().isEmpty()) {
                    reason = "Reported " + type + " by user";
                }

                Loan loan;
                if ("lost".equalsIgnoreCase(type)) {
                    loan = manager.reportLost(loanId, userId, reason);
                } else {
                    loan = manager.reportDamaged(loanId, userId, reason);
                }
                sendJson(exchange, 200, "{\"success\":true,\"status\":\"" + loan.getStatus() + "\"}");
            } catch (Exception e) {
                sendJson(exchange, 400, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        }
    }

    private class ScoreHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            String query = exchange.getRequestURI().getQuery();
            int userId = -1;
            if (query != null && query.contains("userId=")) {
                try {
                    for (String part : query.split("&")) {
                        if (part.startsWith("userId=")) {
                            userId = Integer.parseInt(part.substring(7));
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (userId == -1) {
                sendJson(exchange, 400, "{\"error\":\"userId parameter is required\"}");
                return;
            }

            try {
                User user = manager.findUser(userId);
                int score = manager.calculateScore(user);
                String report = manager.getScoreReport(user);
                sendJson(exchange, 200, "{\"userId\":" + userId + ",\"score\":" + score + ",\"report\":\"" + escape(report) + "\"}");
            } catch (InvalidLoanException e) {
                sendJson(exchange, 404, "{\"error\":\"User not found\"}");
            }
        }
    }

    private class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String pathStr = exchange.getRequestURI().getPath();
            if (pathStr.equals("/") || pathStr.isEmpty()) {
                pathStr = "/index.html";
            }

            Path filePath = staticDir.resolve(pathStr.startsWith("/") ? pathStr.substring(1) : pathStr).normalize();
            if (!filePath.startsWith(staticDir) || !Files.exists(filePath) || Files.isDirectory(filePath)) {
                String notFound = "404 Not Found";
                exchange.sendResponseHeaders(404, notFound.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(notFound.getBytes());
                }
                return;
            }

            String contentType = getContentType(filePath.getFileName().toString());
            exchange.getResponseHeaders().set("Content-Type", contentType);
            byte[] bytes = Files.readAllBytes(filePath);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    // ========================================================= JSON & Serialization

    private String buildStateJson() {
        ArrayList<User> users = manager.getAllUsers();
        ArrayList<Item> items = manager.getAllItems();
        ArrayList<BorrowRequest> requests = manager.getAllRequests();
        ArrayList<Loan> loans = manager.getAllLoans();

        // Map items with active loans and pending requests
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        // users
        sb.append("\"users\":[");
        for (int i = 0; i < users.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(userToJson(users.get(i)));
        }
        sb.append("],");

        // items
        sb.append("\"items\":[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            Item it = items.get(i);
            int maxDays = manager.getMaxDaysFor(it);

            // Check if there is an active loan
            Loan activeLoan = null;
            for (Loan l : loans) {
                if (l.isActive() && l.getItem().getItemId() == it.getItemId()) {
                    activeLoan = l;
                    break;
                }
            }

            // Check if there is a pending request
            BorrowRequest pendingReq = null;
            for (BorrowRequest r : requests) {
                if (r.isPending() && r.getItem().getItemId() == it.getItemId()) {
                    pendingReq = r;
                    break;
                }
            }

            String statusStr = it.getStatus().name().toLowerCase();
            String borrowerName = activeLoan != null ? activeLoan.getBorrower().getName() : "";
            int borrowerId = activeLoan != null ? activeLoan.getBorrower().getUserId() : 0;
            String dueDate = activeLoan != null ? activeLoan.getDueDate().toString() : "";
            int loanId = activeLoan != null ? activeLoan.getLoanId() : 0;

            String requesterName = pendingReq != null ? pendingReq.getBorrower().getName() : "";
            int requesterId = pendingReq != null ? pendingReq.getBorrower().getUserId() : 0;
            int reqDays = pendingReq != null ? pendingReq.getRequestedDays() : 0;
            int reqId = pendingReq != null ? pendingReq.getRequestId() : 0;

            sb.append("{");
            sb.append("\"id\":").append(it.getItemId()).append(",");
            sb.append("\"name\":\"").append(escape(it.getName())).append("\",");
            sb.append("\"cat\":\"").append(escape(it.getCategory())).append("\",");
            sb.append("\"owner\":\"").append(escape(it.getOwner().getName())).append("\",");
            sb.append("\"ownerId\":").append(it.getOwner().getUserId()).append(",");
            sb.append("\"note\":\"").append(escape(it.getDetails())).append("\",");
            sb.append("\"max\":").append(maxDays).append(",");
            sb.append("\"status\":\"").append(statusStr).append("\",");
            sb.append("\"borrower\":\"").append(escape(borrowerName)).append("\",");
            sb.append("\"borrowerId\":").append(borrowerId).append(",");
            sb.append("\"due\":\"").append(dueDate).append("\",");
            sb.append("\"loanId\":").append(loanId).append(",");
            sb.append("\"requester\":\"").append(escape(requesterName)).append("\",");
            sb.append("\"requesterId\":").append(requesterId).append(",");
            sb.append("\"requestId\":").append(reqId).append(",");
            sb.append("\"days\":").append(reqDays);
            sb.append("}");
        }
        sb.append("],");

        // log / history from completed loans
        sb.append("\"log\":[");
        int logCount = 0;
        for (Loan l : loans) {
            if (!l.isActive()) {
                if (logCount > 0) sb.append(",");
                String result = "ontime";
                if (l.getStatus() == Loan.Status.LOST) result = "lost";
                else if (l.getStatus() == Loan.Status.DAMAGED) result = "damaged";
                else if (l.wasReturnedLate()) result = "late";

                LocalDate date = l.getReturnDate() != null ? l.getReturnDate() : l.getDueDate();

                sb.append("{");
                sb.append("\"loanId\":").append(l.getLoanId()).append(",");
                sb.append("\"name\":\"").append(escape(l.getItem().getName())).append("\",");
                sb.append("\"owner\":\"").append(escape(l.getOwner().getName())).append("\",");
                sb.append("\"borrower\":\"").append(escape(l.getBorrower().getName())).append("\",");
                sb.append("\"borrowerId\":").append(l.getBorrower().getUserId()).append(",");
                sb.append("\"result\":\"").append(result).append("\",");
                sb.append("\"date\":\"").append(date.toString()).append("\"");
                sb.append("}");
                logCount++;
            }
        }
        sb.append("]");

        sb.append("}");
        return sb.toString();
    }

    private String userToJson(User u) {
        int score = manager.calculateScore(u);
        return String.format("{\"id\":%d,\"name\":\"%s\",\"room\":\"%s\",\"score\":%d}",
                u.getUserId(), escape(u.getName()), escape(u.getRoom()), score);
    }

    private String itemToJson(Item it) {
        return String.format("{\"id\":%d,\"name\":\"%s\",\"cat\":\"%s\",\"owner\":\"%s\",\"ownerId\":%d,\"status\":\"%s\"}",
                it.getItemId(), escape(it.getName()), escape(it.getCategory()),
                escape(it.getOwner().getName()), it.getOwner().getUserId(), it.getStatus().name().toLowerCase());
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '"') sb.append("\\\"");
            else if (c == '\\') sb.append("\\\\");
            else if (c == '\b') sb.append("\\b");
            else if (c == '\f') sb.append("\\f");
            else if (c == '\n') sb.append("\\n");
            else if (c == '\r') sb.append("\\r");
            else if (c == '\t') sb.append("\\t");
            else if (c < 32) sb.append(String.format("\\u%04x", (int) c));
            else sb.append(c);
        }
        return sb.toString();
    }

    private void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        addCorsHeaders(exchange);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = is.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            return baos.toString(StandardCharsets.UTF_8);
        }
    }

    private Map<String, String> parseJsonBody(String body) {
        Map<String, String> map = new HashMap<>();
        if (body == null || body.trim().isEmpty()) return map;
        String trimmed = body.trim();

        // 1. Regex pattern for JSON key-value extraction
        // Handles "key":"val", "key": "val", key: val, etc.
        java.util.regex.Pattern jsonPattern = java.util.regex.Pattern.compile("(?i)[\"']?([a-zA-Z0-9_]+)[\"']?\\s*:\\s*(?:\"([^\"]*)\"|'([^']*)'|([^,\\}\\]\\s]+))");
        java.util.regex.Matcher matcher = jsonPattern.matcher(trimmed);
        while (matcher.find()) {
            String key = matcher.group(1);
            String val = matcher.group(2) != null ? matcher.group(2) : (matcher.group(3) != null ? matcher.group(3) : matcher.group(4));
            if (val != null) {
                val = val.trim();
                map.put(key.trim(), val);
                map.put(key.trim().toLowerCase(), val);
            }
        }

        // 2. Also support url-encoded form body if JSON regex didn't find anything
        if (map.isEmpty() && trimmed.contains("=")) {
            for (String pair : trimmed.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    try {
                        String k = URLDecoder.decode(pair.substring(0, eq).trim(), StandardCharsets.UTF_8.name());
                        String v = URLDecoder.decode(pair.substring(eq + 1).trim(), StandardCharsets.UTF_8.name());
                        map.put(k, v);
                        map.put(k.toLowerCase(), v);
                    } catch (Exception ignored) {}
                }
            }
        }
        return map;
    }

    private String getContentType(String filename) {
        if (filename.endsWith(".html")) return "text/html; charset=utf-8";
        if (filename.endsWith(".css")) return "text/css; charset=utf-8";
        if (filename.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (filename.endsWith(".json")) return "application/json; charset=utf-8";
        if (filename.endsWith(".png")) return "image/png";
        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) return "image/jpeg";
        if (filename.endsWith(".svg")) return "image/svg+xml";
        return "text/plain; charset=utf-8";
    }
}
