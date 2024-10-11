import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;

@WebServlet(value = "/skiers/*")
public class SkierServlet extends HttpServlet {

    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        res.setContentType("text/plain");
        String urlPath = req.getPathInfo();

        // check we have a URL!
        if (urlPath == null || urlPath.isEmpty()) {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            res.getWriter().write("Please provide more path parameters, e.g., /skiers/{resortID}/seasons/{seasonID}/days/{dayID}/skiers/{skierID}");
            return;
        }

        String[] urlParts = urlPath.split("/");
        // Validate the URL path
        if (!isUrlValid(urlParts)) {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            res.getWriter().write("Invalid URL path");
        } else {
        res.setStatus(HttpServletResponse.SC_OK);
        // do any sophisticated processing with urlParts which contains all the url params
        // TODO: process url params in `urlParts`
        res.getWriter().write("Valid path: " + urlPath);
    }}

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        res.setContentType("application/json");
        // Log the request for debugging
        BufferedReader reader = req.getReader();
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        System.out.println("Received POST request body: " + sb.toString());

        res.setStatus(HttpServletResponse.SC_OK);
        res.getWriter().write("{\"message\": \"POST request received successfully\"}");
    }



    private boolean isUrlValid(String[] urlPath) {
        // URL should be in the format: /skiers/{resortID}/seasons/{seasonID}/days/{dayID}/skiers/{skierID}
        // Expected format: /skiers/12/seasons/2019/day/1/skier/123
        if (urlPath.length == 8) {
            return "seasons".equals(urlPath[2]) && "days".equals(urlPath[4]) && "skiers".equals(urlPath[6]) &&
                    isNumeric(urlPath[1]) && isNumeric(urlPath[3]) && isNumeric(urlPath[5]) && isNumeric(urlPath[7]);
        }
        return false;
    }

    private boolean isNumeric(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
