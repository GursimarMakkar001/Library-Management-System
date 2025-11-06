// Importing necessary classes for HTTP server functionality
import com.sun.net.httpserver.HttpServer;     // Handles the creation of the server
import com.sun.net.httpserver.HttpExchange;   // Represents an HTTP request/response pair

// Importing networking and I/O classes
import java.net.InetSocketAddress;  // Defines IP address and port
import java.net.URI;                // Handles URI parsing
import java.io.*;                   // Includes InputStream, OutputStream, BufferedReader, etc.
import java.net.URLDecoder;         // Decodes URL-encoded strings
import java.util.*;                 // Includes List, ArrayList, Map, HashMap, etc.

// Main application class
public class BookApp {

    // Inner class representing a book entity
    static class Book {
        int id;  // Book ID
        String title, author;  // Book title and author

        // Constructor to initialize book details
        Book(int id, String title, String author) {
            this.id = id;
            this.title = title;
            this.author = author;
        }
    }

    static List<Book> books = new ArrayList<>();  // List to store all books
    static int currentId = 1;                     // Keeps track of the next ID to assign
    static final String DATA_FILE = "books.txt";  // File for persistent storage

    // Entry point of the application
    public static void main(String[] args) throws IOException {
        loadBooks();  // Load existing books from file

        // Create an HTTP server on port 8000
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);

        // Define different endpoints and their handlers
        server.createContext("/", BookApp::handleHome);
        server.createContext("/add", BookApp::handleAdd);
        server.createContext("/delete", BookApp::handleDelete);
        server.createContext("/edit", BookApp::handleEdit);
        server.createContext("/update", BookApp::handleUpdate);
        server.createContext("/search", BookApp::handleSearch);
        server.createContext("/export", BookApp::handleExport);

        server.setExecutor(null);  // Use default executor
        server.start();

        System.out.println("✅ Server running at http://localhost:8000/");

        // Optional graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("🛑 Shutting down server...");
            server.stop(0);
        }));
    }

    // Handles rendering the home page with book list and forms
    static void handleHome(HttpExchange ex) throws IOException {
        StringBuilder sb = new StringBuilder();

        sb.append("<html><head><style>")
          .append("body{font-family:sans-serif;padding:20px;}")
          .append("table{border-collapse:collapse;width:100%}")
          .append("th,td{border:1px solid #aaa;padding:8px;text-align:left}")
          .append("</style></head><body>");

        sb.append("<h2>Add Book</h2>")
          .append("<form method='POST' action='/add'>")
          .append("Title: <input name='title' required> ")
          .append("Author: <input name='author' required> ")
          .append("<button type='submit'>Add</button></form>");

        // Search form
        sb.append("<form method='GET' action='/search'>")
          .append("<input name='q' placeholder='Search...'>")
          .append("<button type='submit'>Search</button></form>");

        // Export link
        sb.append("<a href='/export'>Export CSV</a>");

        // Display book list
        sb.append("<h2>Book List</h2><table><tr><th>ID</th><th>Title</th><th>Author</th><th>Action</th></tr>");
        for (Book b : books) {
            sb.append("<tr><td>").append(b.id)
              .append("</td><td>").append(b.title)
              .append("</td><td>").append(b.author)
              .append("</td><td>")
              .append("<a href='/edit?id=").append(b.id).append("'>Edit</a> | ")
              .append("<a href='/delete?id=").append(b.id).append("'>Delete</a>")
              .append("</td></tr>");
        }
        sb.append("</table></body></html>");

        sendHtml(ex, sb.toString());
    }

    // Handles POST request to add a new book
    static void handleAdd(HttpExchange ex) throws IOException {
        if (ex.getRequestMethod().equalsIgnoreCase("POST")) {
            Map<String, String> form = parseForm(ex);
            books.add(new Book(currentId++, form.get("title"), form.get("author")));
            saveBooks();
        }
        redirect(ex, "/");
    }

    // Handles book deletion by ID
    static void handleDelete(HttpExchange ex) throws IOException {
        int id = Integer.parseInt(getQueryParam(ex.getRequestURI(), "id"));
        books.removeIf(b -> b.id == id);
        saveBooks();
        redirect(ex, "/");
    }

    // Handles form page to edit a book
    static void handleEdit(HttpExchange ex) throws IOException {
        int id = Integer.parseInt(getQueryParam(ex.getRequestURI(), "id"));
        Book target = books.stream().filter(b -> b.id == id).findFirst().orElse(null);
        if (target == null) {
            redirect(ex, "/");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<html><body><h2>Edit Book</h2>")
          .append("<form method='POST' action='/update'>")
          .append("<input type='hidden' name='id' value='").append(target.id).append("'>")
          .append("Title: <input name='title' value='").append(target.title).append("'> ")
          .append("Author: <input name='author' value='").append(target.author).append("'> ")
          .append("<button type='submit'>Update</button></form>")
          .append("</body></html>");

        sendHtml(ex, sb.toString());
    }

    // Handles POST request to update a book
    static void handleUpdate(HttpExchange ex) throws IOException {
        if (ex.getRequestMethod().equalsIgnoreCase("POST")) {
            Map<String, String> form = parseForm(ex);
            int id = Integer.parseInt(form.get("id"));
            for (Book b : books) {
                if (b.id == id) {
                    b.title = form.get("title");
                    b.author = form.get("author");
                    break;
                }
            }
            saveBooks();
        }
        redirect(ex, "/");
    }

    // Handles search functionality
    static void handleSearch(HttpExchange ex) throws IOException {
        String query = getQueryParam(ex.getRequestURI(), "q");
        if (query == null) query = "";
        query = query.toLowerCase();

        StringBuilder sb = new StringBuilder("<html><body><a href='/'>← Back</a><h2>Search Results</h2><table>");
        sb.append("<tr><th>ID</th><th>Title</th><th>Author</th><th>Action</th></tr>");

        for (Book b : books) {
            if (b.title.toLowerCase().contains(query) || b.author.toLowerCase().contains(query)) {
                sb.append("<tr><td>").append(b.id).append("</td><td>")
                  .append(b.title).append("</td><td>").append(b.author).append("</td><td>")
                  .append("<a href='/edit?id=").append(b.id).append("'>Edit</a> | ")
                  .append("<a href='/delete?id=").append(b.id).append("'>Delete</a></td></tr>");
            }
        }
        sb.append("</table></body></html>");
        sendHtml(ex, sb.toString());
    }

    // Exports book list as a CSV file
    static void handleExport(HttpExchange ex) throws IOException {
        StringBuilder csv = new StringBuilder("ID,Title,Author\n");
        for (Book b : books) {
            csv.append(b.id).append(",")
               .append(escapeCsv(b.title)).append(",")
               .append(escapeCsv(b.author)).append("\n");
        }

        byte[] out = csv.toString().getBytes("UTF-8");
        ex.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
        ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=books.csv");
        ex.sendResponseHeaders(200, out.length);

        try (OutputStream os = ex.getResponseBody()) {
            os.write(out);
        }
    }

    // Escapes text for safe CSV export
    private static String escapeCsv(String text) {
        if (text == null) return "";
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            text = text.replace("\"", "\"\"");
            text = "\"" + text + "\"";
        }
        return text;
    }

    // Parses form data from POST requests
    static Map<String, String> parseForm(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), "UTF-8");
        Map<String, String> map = new HashMap<>();
        for (String pair : body.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                map.put(URLDecoder.decode(kv[0], "UTF-8"), URLDecoder.decode(kv[1], "UTF-8"));
            }
        }
        return map;
    }

    // Retrieves a query parameter from the URL
    static String getQueryParam(URI uri, String key) {
        String query = uri.getQuery();
        if (query == null) return "";
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                try {
                    return URLDecoder.decode(kv[1], "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    return "";
                }
            }
        }
        return "";
    }

    // Sends HTML content in the HTTP response
    static void sendHtml(HttpExchange ex, String html) throws IOException {
        byte[] data = html.getBytes("UTF-8");
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }

    // Redirects the client to a new location
    static void redirect(HttpExchange ex, String location) throws IOException {
        ex.getResponseHeaders().set("Location", location);
        ex.sendResponseHeaders(302, -1);
        ex.close();
    }

    // Saves books to a file for persistence
    static void saveBooks() throws IOException {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(DATA_FILE), "UTF-8"))) {
            for (Book b : books) {
                writer.write(b.id + "," + b.title + "," + b.author + "\n");
            }
        }
    }

    // Loads books from the file at startup
    static void loadBooks() throws IOException {
        File file = new File(DATA_FILE);
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", 3);
                if (parts.length == 3) {
                    int id = Integer.parseInt(parts[0]);
                    books.add(new Book(id, parts[1], parts[2]));
                    currentId = Math.max(currentId, id + 1);
                }
            }
        }
    }
}
