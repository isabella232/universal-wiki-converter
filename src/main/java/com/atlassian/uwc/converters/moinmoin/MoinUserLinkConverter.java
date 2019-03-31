package com.atlassian.uwc.converters.moinmoin;

import com.atlassian.uwc.converters.BaseConverter;
import com.atlassian.uwc.ui.FileUtils;
import com.atlassian.uwc.ui.Page;

import java.io.File;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts links to MoinMoin users (i.e. WikiNames to user names in page content)
 * into links / mentions in Confluence (i.e. [~username])
 */
public class MoinUserLinkConverter extends BaseConverter {
    private static final Charset charset = Charset.defaultCharset();
    private static final String USERS_PATH_PROPERTY = "userlink-converter-users-path";
    private static final Pattern WIKI_NAME_PATTERN = Pattern.compile("(^|\\s|/)([A-Z][a-z]+[A-Z][\\w]+)");

    private Map<String, String> origNameToUserId;

    @Override
    public void setProperties(Properties properties) {
        super.setProperties(properties);
        // After receiving the properties, we can build the origNameToUserId mapping, if the users-path is set.
        String userFileDir = (String) properties.get(USERS_PATH_PROPERTY);
        if (userFileDir != null) {
            origNameToUserId = getOrigNameToUserIdMapping(userFileDir);
        }
    }

    @Override
    public void convert(Page page) {
        String input = page.getOriginalText();

        Matcher wikiNameFinder = WIKI_NAME_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();
        boolean found = false;
        while (wikiNameFinder.find()) {
            String wikiName = wikiNameFinder.group(2);
            if (origNameToUserId.containsKey(wikiName)) {
                found = true;
                String prefix = wikiNameFinder.group(1);
                String userId = origNameToUserId.get(wikiName);
                if (userId != null) {
                    wikiNameFinder.appendReplacement(sb, prefix + "[~" + userId + "]");
                } else {
                    // escape WikiName, so it doesn't become a link
                    wikiNameFinder.appendReplacement(sb, prefix + "!" + wikiName);
                }
            }
        }

        if (found) {
            wikiNameFinder.appendTail(sb);
            page.setConvertedText(sb.toString());
        } else {
            page.setConvertedText(input);
        }

    }

    private Map<String, String> getOrigNameToUserIdMapping(String userFileDir) {
        Map<String, String> res = new HashMap<String, String>();

        Pattern origNameFinder = Pattern.compile("^name=(\\S+)", Pattern.MULTILINE);
        Pattern userIdFinder = Pattern.compile("^aliasname=(\\S+)", Pattern.MULTILINE);

        try{
            File userdir = new File(userFileDir);
            File[] userFiles = userdir.listFiles();
            if (userFiles != null) {
                for( File f : userFiles){

                    // leave directories out
                    if( f.isDirectory() ) continue;

                    String cont = FileUtils.readTextFile(f, charset);
                    Matcher origNameMatcher = origNameFinder.matcher(cont);
                    if (origNameMatcher.find()){
                        String origName = origNameMatcher.group(1);
                        Matcher userIdMatcher = userIdFinder.matcher(cont);
                        if (userIdMatcher.find()) {
                            res.put(origName, userIdMatcher.group(1));
                        } else {
                            res.put(origName, null);
                        }
                    }

                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return res;
    }
}
