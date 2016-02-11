package com.zestedesavoir.zestwriter.utils;

import com.zestedesavoir.zestwriter.MainApp;
import com.zestedesavoir.zestwriter.model.MetadataContent;
import javafx.util.Pair;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


public class ZdsHttp {

	String idUser;
	String login;
	String password;
	String hostname;
	String port;
	String protocol;
	String workspace;
	boolean authenticated = false;
	List<MetadataContent> contentListOnline;
	HttpClient client;
	CookieStore cookieStore;
	String cookies;
	HttpClientContext context;
	String localSlug;
	String localType;

	private final Logger logger = LoggerFactory.getLogger(ZdsHttp.class);
	private final static String USER_AGENT= "Mozilla/5.0";

	public String getLogin() {
		return login;
	}


	public String getLocalSlug() {
		return localSlug;
	}



	public void setLocalSlug(String localSlug) {
		this.localSlug = localSlug;
	}



	public String getLocalType() {
		return localType;
	}



	public void setLocalType(String localType) {
		this.localType = localType;
	}



	public String getWorkspace() {
		return workspace;
	}


	public void setWorkspace(String workspace) {
		this.workspace = workspace;
	}


	public HttpClientContext getContext() {
		return context;
	}

	public List<MetadataContent> getContentListOnline() {
		return contentListOnline;
	}

	public void setContext(HttpClientContext context) {
		this.context = context;
	}

	public String getBaseUrl() {
		if(this.port.equals("80")) {
			return this.protocol + "://" + this.hostname;
		} else {
			return this.protocol + "://" + this.hostname + ":" + this.port;
		}
	}

	public String getLoginUrl() {
		return getBaseUrl() + "/membres/connexion/?next=/";
	}

	public String getViewMemberUrl(String username) {
		return getBaseUrl() + "/membres/voir/"+username;
	}

	public String getPersonalTutorialUrl() {
		return getBaseUrl() + "/contenus/tutoriels/" + idUser + "/";
	}

	public String getPersonalArticleUrl() {
		return getBaseUrl() + "/contenus/articles/" + idUser + "/";
	}

	public String getDownloadDraftContentUrl(String id, String slug) {
		return getBaseUrl() + "/contenus/telecharger/" + id + "/" + slug + "/";
	}

	public String getImportContenttUrl(String idContent, String slugContent) {
		return getBaseUrl() + "/contenus/importer/" + idContent + "/" + slugContent + "/";
	}

	public String getViewContenttUrl(String idContent, String slugContent) {
		return getBaseUrl() + "/contenus/" + idContent + "/" + slugContent + "/";
	}

	public String getOnlineContentPathDir() {
		return this.workspace + File.separator + "online";
	}

	public String getOfflineContentPathDir() {
		return this.workspace + File.separator + "offline";
	}


	public void initContext() {
		context = HttpClientContext.create();
		cookieStore = new BasicCookieStore();
		context.setCookieStore(cookieStore);
		PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
		// Increase max total connection to 200
		cm.setMaxTotal(500);
		// Increase default max connection per route to 20
		cm.setDefaultMaxPerRoute(20);
		client = HttpClientBuilder.create().setRedirectStrategy(new LaxRedirectStrategy()).setConnectionManager(cm).build();
		contentListOnline = new ArrayList<MetadataContent>();
		// create workspace if doesn't exist
		String[] dirPaths = { this.workspace, getOnlineContentPathDir(), getOfflineContentPathDir() };
		for (String dirPath : dirPaths) {
			File theDir = new File(dirPath);
			if (!theDir.exists()) {
				try {
					theDir.mkdir();
				} catch (SecurityException se) {
					logger.error("Cannot create directory", se);
				}
			}
		}
	}

	public ZdsHttp(Properties prop) {
		super();
		JFileChooser fr = new JFileChooser();
		FileSystemView fw = fr.getFileSystemView();
		workspace = fw.getDefaultDirectory().getAbsolutePath() + File.separator;
		if (prop.containsKey("data.directory")) {
			workspace += prop.getProperty("data.directory");
		} else {
			workspace += "zwriter-workspace";
		}
		protocol = prop.getProperty("server.protocol", "http");
		hostname = prop.getProperty("server.host", "localhost");
		port = prop.getProperty("server.port", "8000");
		initContext();
	}

	public String getCookieValue(CookieStore cookieStore, String cookieName) {
		for (Cookie cookie : cookieStore.getCookies()) {
			if (cookie.getName().equals(cookieName)) {
				return cookie.getValue();
			}
		}
		return null;
	}

	public String getIdByUsername() throws IOException {
		String url = getViewMemberUrl(login);
		HttpGet get = new HttpGet(url);
		logger.debug("Tentative de connexion à " + url);

		// FIXME : wrap around Runnable
		HttpResponse response = client.execute(get, context);

		if (response.getStatusLine().getStatusCode() != 200) {
			logger.warn("Impossible de joindre l'url " + url);
			return null;
		}

		logger.debug("Url joignable " + url);

		InputStream is = response.getEntity().getContent();
		Document doc = Jsoup.parse(is, "UTF-8", url);
		String link = doc.select("a.mobile-menu-sublink").first().attr("href");
		for (String param : link.split("/")) {
			try{
		        int x = Integer.parseInt(param);
		        logger.debug("Id Utilisateur trouvé : " + x);
		        return param;
		     }
		     catch(NumberFormatException e){
		        continue;
		     }
		}
		return null;
	}

	private Pair<Integer, String> sendPost(String url, HttpEntity entity) throws UnsupportedEncodingException {
		HttpPost post = new HttpPost(url);
		try {
			// add header
			post.setHeader("Host", this.hostname);
			post.setHeader("User-Agent", USER_AGENT);
			post.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
			post.setHeader("Accept-Language", "fr-FR");
			post.setHeader("Cookie", this.cookies);
			post.setHeader("Connection", "keep-alive");
			post.setHeader("Referer", url);
			//post.setHeader("Content-Type", "application/x-www-form-urlencoded");

			post.setEntity(entity);
			HttpResponse response = client.execute(post, context);

			int responseCode = response.getStatusLine().getStatusCode();
			BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));

			StringBuffer result = new StringBuffer();
			String line = "";
			while ((line = rd.readLine()) != null) {
				result.append(line);
			}

			return new Pair<>(responseCode, result.toString());
		} catch (IOException e) {
			e.printStackTrace();
		}

		return new Pair<>(500, null);
	}

	public boolean login(String login, String password) throws IOException {
		this.login = login;
		this.password = password;
		idUser = getIdByUsername();
		logger.info("L'identifiant de l'utilisateur " + this.login + " est : " + idUser);

		HttpGet get = new HttpGet(getLoginUrl());
		HttpResponse response = client.execute(get, context);
		this.cookies = response.getFirstHeader("Set-Cookie").getValue();

		List<NameValuePair> urlParameters = new ArrayList<>();
		urlParameters.add(new BasicNameValuePair("username", this.login));
		urlParameters.add(new BasicNameValuePair("password", this.password));
		urlParameters.add(new BasicNameValuePair("csrfmiddlewaretoken", getCookieValue(cookieStore, "csrftoken")));

		Pair<Integer, String> pair = sendPost(getLoginUrl(), new UrlEncodedFormEntity(urlParameters));
		if (pair.getKey()==200 && pair.getValue().contains("my-account-dropdown")) {
			this.authenticated = true;
			logger.info("Utilisateur " + this.login + " connecté");
		} else {
			logger.debug("Utilisateur " + this.login + " non connecté via " + getLoginUrl());
		}
		return this.authenticated;
	}

	public void logout() {
		this.login = null;
		this.password = null;
		this.idUser = null;
		this.cookieStore = null;
		this.authenticated = false;
	}

	public boolean importContent(String filePath, String targetId, String targetSlug) throws IOException {
		logger.debug("Tentative d'import via l'url : " + getImportContenttUrl(targetId, targetSlug));
		HttpGet get = new HttpGet(getImportContenttUrl(targetId, targetSlug));
		HttpResponse response = client.execute(get, context);
		this.cookies = response.getFirstHeader("Set-Cookie").getValue();

		// load file in form
		FileBody cbFile = new FileBody(new File(filePath));
		MultipartEntityBuilder builder = MultipartEntityBuilder.create();
		builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
		builder.addPart("archive", cbFile);
		builder.addPart("csrfmiddlewaretoken", new StringBody(getCookieValue(cookieStore, "csrftoken"), ContentType.MULTIPART_FORM_DATA));

		Pair<Integer, String> resultPost = sendPost(getImportContenttUrl(targetId, targetSlug), builder.build());
		int statusCode = resultPost.getKey();

		switch (statusCode) {
			case 404:
				logger.error("Your target id and slug is incorrect, please give us real information");
			case 403:
				logger.error("Your are not authorized to do this task. Please check if your are logged in");
		}

		return statusCode == 200;
	}

	public void initInfoOnlineContent(String type) throws IOException {
		Objects.requireNonNull(type);
		logger.info("Initialisation des metadonnées contenus en ligne de type " + type);
		String url;
		switch (type) {
			case "tutorial":
				url = getPersonalTutorialUrl();
				break;
			case "article":
				url = getPersonalArticleUrl();
				break;
			default:
				throw new UnsupportedOperationException("Unknow type of content " + type);
		}
		logger.info("Tentative de joindre l'url : " + url);
		HttpGet get = new HttpGet(url);
		HttpResponse response = client.execute(get, context);
		this.cookies = response.getFirstHeader("Set-Cookie").toString();
		logger.info("Tentative réussie");

		InputStream is = response.getEntity().getContent();

		Document doc = Jsoup.parse(is, "UTF-8", url);
		Elements sections = doc.select("article");
		for (Element section : sections) {
			Elements links = section.getElementsByTag("a");
			for (Element link : links) {
				String ref = link.attr("href").trim();
				logger.trace("Chaine à decrypter pour trouver le slug : "+ref);
				if (ref.startsWith("/contenus/")) {
					String[] tab = ref.split("/");
					MetadataContent onlineContent = new MetadataContent(tab[2], tab[3], type);
					getContentListOnline().add(onlineContent);
				}
			}
		}
		logger.info("Contenu de type #0" + type + " chargés en mémoire : " + getContentListOnline());
		closeSilently(is);
	}

	public String getTargetSlug(String targetId, String type) {
		for (MetadataContent metadata : contentListOnline) {
			if (metadata.getId().equals(targetId) && metadata.getType().equalsIgnoreCase(type)) {
				return metadata.getSlug();
			}
		}
		return null;
	}

	public void downloaDraft(String targetId, String type) {
		String targetSlug = getTargetSlug(targetId, type);
		HttpGet get = new HttpGet(getDownloadDraftContentUrl(targetId, targetSlug));
		logger.debug("Tentative de téléchargement via le lien : "+getDownloadDraftContentUrl(targetId, targetSlug));
		InputStream is = null;
		try {
			HttpResponse response = client.execute(get);
			is = response.getEntity().getContent();
			String filePath = getOnlineContentPathDir() + File.separator + targetSlug + ".zip";
			Files.copy(is, Paths.get(filePath));
		} catch (IOException ioe) {
			logger.error("Could not download draft", ioe);
		} finally {
			closeSilently(is);
		}
	}

	public void unzipOnlineContent(String zipFilePath) throws IOException {

		byte[] buffer = new byte[1024];
		try {
			String dirname = Paths.get(zipFilePath).getFileName().toString();
			dirname = dirname.substring(0, dirname.length() - 4);
			// create output directory is not exists
			File folder = new File(getOfflineContentPathDir() + File.separator + dirname);
			logger.debug("Tentative de dezippage de "+zipFilePath+" dans "+folder.getAbsolutePath());
			if (!folder.exists()) {
				folder.mkdir();
				// get the zip file content
				ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath));
				// get the zipped file list entry
				ZipEntry ze = zis.getNextEntry();
				System.out.println(ze);

				while (ze != null) {

					String fileName = ze.getName();
					logger.trace("Traitement du fichier : "+fileName);
					File newFile = new File(folder + File.separator + fileName);
					new File(newFile.getParent()).mkdirs();

					FileOutputStream fos = new FileOutputStream(newFile);

					int len;
					while ((len = zis.read(buffer)) > 0) {
						fos.write(buffer, 0, len);
					}

					fos.close();
					ze = zis.getNextEntry();
				}

				zis.closeEntry();
				zis.close();
				logger.info("Dézippage dans "+folder.getAbsolutePath()+" réalisé avec succès");
			}
			else {
				logger.debug("Le répertoire dans lequel vous souhaitez dezipper existe déjà ");
			}

		} catch (IOException ex) {
			ex.printStackTrace();
			logger.debug("Echec de dezippage dans "+zipFilePath);
		}
	}

	public boolean isAuthenticated() {
		return authenticated;
	}


	public static void main(String[] args) {
		Properties prop = new Properties();

		try (InputStream input = MainApp.class.getClassLoader().getResourceAsStream("config.properties")) {
			prop.load(input);
			ZdsHttp zdsutils = new ZdsHttp(prop);
			if(zdsutils.login("admin", "admin")) {
				zdsutils.initInfoOnlineContent("tutorial");
				zdsutils.initInfoOnlineContent("article");

				for (MetadataContent meta : zdsutils.getContentListOnline()) {
					zdsutils.downloaDraft(meta.getId(), meta.getType());
					zdsutils.unzipOnlineContent(zdsutils.getOnlineContentPathDir() + File.separator + meta.getSlug() + ".zip");
				}
			}
			else {
				System.out.println("Echec de connexion");
			}


		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private void closeSilently(InputStream... streams) {
		for (InputStream is : streams) {
			if (is == null) continue;
			try {
				is.close();
			} catch(IOException ioe) {
				// log.warn("Could not close stream");
			}
		}
	}
}
