package prolongationENT;

import java.io.IOException;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.LoggerFactory;

import static prolongationENT.Utils.*;

public class LoaderJs {
    Conf.Main conf;
    String js;
    String jsHash;
    
    org.slf4j.Logger log = LoggerFactory.getLogger(LoaderJs.class);

    public LoaderJs(HttpServletRequest request, Conf.Main conf) {
        this.conf = conf;
        js = compute(request, conf.theme);
        jsHash = computeMD5(js);
    }
    
    void loader_js(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (!jsHash.equals(request.getParameter("v"))) {
            int one_hour = 60 * 60;
            // redirect to versioned loader.js which has long cache time
            setCacheControlMaxAge(response, one_hour);
            Utils.sendRedirect(response, "loader.js?v=" + urlencode(jsHash));
        } else {
            int one_year = 60 * 60 * 24 * 365;
            response.setHeader("Etag", jsHash);
            respond_js(response, one_year, js);
        }
    }
    
    String compute(HttpServletRequest request, String theme) {
        String helpers_js = file_get_contents(request, "lib/helpers.js");
        String main_js = file_get_contents(request, "lib/main.js");
        String loader_js = file_get_contents(request, "lib/loader-base.js");

        String js_css = json_encode(
            asMap("base",    get_css_with_absolute_url(request, theme, "main.css"))
             .add("desktop", get_css_with_absolute_url(request, theme, "desktop.css"))
        );

        String templates = json_encode(
            asMap("header", theme_file_contents(request, theme, "templates/header.html"))
             .add("footer", theme_file_contents(request, theme, "templates/footer.html"))
        );

        Map<String, Object> js_conf =
            objectFieldsToMap(conf, "prolongationENT_url", "esupUserApps_url", "cas_login_url", "uportal_base_url", "layout_url",
                              "cas_impersonate", "disableLocalStorage", 
                              "time_before_checking_browser_cache_is_up_to_date", "ent_logout_url");
        js_conf.put("theme", theme);

        String plugins =
            file_get_contents(request, "lib/plugins.js") +
            file_get_contents(request, "lib/" + theme + ".js");
        for (String plugin : conf.plugins) {
            plugins += file_get_contents(request, "lib/plugin-" + plugin + ".js");
        }
        
        return
            "(function () {\n" +
            "if (!window.prolongation_ENT) window.prolongation_ENT = {};\n" +
            file_get_contents(request, "lib/init.js") +
            "pE.CONF = " + json_encode(js_conf) + "\n\n" +
            (conf.disableCSSInlining ? "" : "pE.CSS = " + js_css + "\n\n") +
            "pE.TEMPLATES = " + templates + "\n\n" +
            helpers_js + main_js + "\n\n" +
            plugins + ";" +
            loader_js +
            "})()";
    }

    String theme_file_contents(HttpServletRequest request, String theme, String file) {
        return file_get_contents(request, theme + "/" + file);
    }
    
    String get_css_with_absolute_url(HttpServletRequest request, String theme, String css_file) {
        String s = theme_file_contents(request, theme, css_file);
        return s.replaceAll("(url\\(['\" ]*)(?!['\" ])(?!https?:|/)", "$1" + conf.prolongationENT_url + "/" + theme + "/");
    }

}

