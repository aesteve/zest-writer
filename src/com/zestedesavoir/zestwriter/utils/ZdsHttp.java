package com.zestedesavoir.zestwriter.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileSystemView;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
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
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.zestedesavoir.zestwriter.MainApp;
import com.zestedesavoir.zestwriter.model.MetadataContent;

import javafx.util.Pair;


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
	final Logger logger;
	static String USER_AGENT= "Mozilla/5.0";

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
					se.printStackTrace();
				}
			}
		}
	}

	public ZdsHttp(Properties prop) {
		super();
		logger = LoggerFactory.getLogger(ZdsHttp.class);
		JFileChooser fr = new JFileChooser();
		FileSystemView fw = fr.getFileSystemView();
		if(prop.containsKey("data.directory")) {
			this.workspace = fw.getDefaultDirectory().getAbsolutePath()+File.separator+prop.getProperty("data.directory");
		}
		else {
			this.workspace = fw.getDefaultDirectory().getAbsolutePath()+File.separator+"zwriter-workspace";
		}
		if(prop.containsKey("server.protocol")) {
			this.protocol = prop.getProperty("server.protocol");
		} else {
			this.protocol = "http";
		}

		if(prop.containsKey("server.host")) {
			this.hostname = prop.getProperty("server.host");
			if(prop.containsKey("server.port")) {
				this.port = prop.getProperty("server.port");
			}
		}
		else {
			this.hostname = "localhost";
			this.port = "8000";
		}
		initContext();
	}

	public String getCookieValue(CookieStore cookieStore, String cookieName) {
		String value = null;
		for (Cookie cookie : cookieStore.getCookies()) {

			if (cookie.getName().equals(cookieName)) {
				value = cookie.getValue();
			}
		}
		return value;
	}

	public String getIdByUsername(String username) throws ClientProtocolException, IOException {
		HttpGet get = new HttpGet(getViewMemberUrl(login));
		logger.debug("Tentative de connexion à "+getViewMemberUrl(login));
		HttpResponse response = client.execute(get, context);

		if (response.getStatusLine().getStatusCode() != 200) {
			logger.debug("Impossible de joindre l'url "+getViewMemberUrl(login));
			return null;
		}

		logger.debug("Url joignable "+getViewMemberUrl(login));

		BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
		StringBuffer result = new StringBuffer();
		String line = "";
		while ((line = rd.readLine()) != null) {
			result.append(line);
		}

		Document doc = Jsoup.parse(result.toString());

		String link = doc.select("a.mobile-menu-sublink").first().attr("href");
		List<String> lst = Arrays.asList(link.split("/"));
		for(String param:lst) {
			try{
		        int x = Integer.parseInt(param);
		        logger.debug("Id Utilisateur trouvé : "+x);
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

			return new Pair<Integer, String>(responseCode, result.toString());
		} catch (IOException e) {
			e.printStackTrace();
		}

		return new Pair<Integer, String>(500, null);
	}

	public boolean login(String login, String password) throws ClientProtocolException, IOException {
		this.login = login;
		this.password = password;
		idUser = getIdByUsername(login);
		logger.info("L'identifiant de l'utilisateur "+this.login+" est : "+idUser);

		HttpGet get = new HttpGet(getLoginUrl());
		HttpResponse response = client.execute(get, context);
		this.cookies = response.getFirstHeader("Set-Cookie").getValue();

		List<NameValuePair> urlParameters = new ArrayList<NameValuePair>();
		urlParameters.add(new BasicNameValuePair("username", this.login));
		urlParameters.add(new BasicNameValuePair("password", this.password));
		urlParameters.add(new BasicNameValuePair("csrfmiddlewaretoken", getCookieValue(cookieStore, "csrftoken")));

		Pair<Integer, String> pair = sendPost(getLoginUrl(), new UrlEncodedFormEntity(urlParameters));
		if (pair.getKey()==200 && pair.getValue().contains("my-account-dropdown")) {
			this.authenticated = true;
			logger.info("Utilisateur "+this.login+" connecté");
		} else {
			logger.debug("Utilisateur "+this.login+" non connecté via "+getLoginUrl());
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

	public boolean importContent(String filePath, String targetId, String targetSlug)
			throws ClientProtocolException, IOException {
		logger.debug("Tentative d'import via l'url : "+getImportContenttUrl(targetId, targetSlug));
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
			logger.debug("Your target id and slug is incorrect, please give us real informations");
		case 403:
			logger.debug("Your are not authorize to do this task. Please check if your are login");
		}

		return statusCode == 200;
	}

	public void initInfoOnlineContent(String type) throws ClientProtocolException, IOException {
		HttpGet get = null;

		logger.info("Initialisation des metadonnées contenus en ligne de type "+type);

		if (type.equals("tutorial")) {
			logger.info("Tentative de joindre l'url : "+getPersonalTutorialUrl());
			get = new HttpGet(getPersonalTutorialUrl());
		} else {
			if (type.equals("article")) {
				logger.info("Tentative de joindre l'url : "+getPersonalArticleUrl());
				get = new HttpGet(getPersonalArticleUrl());
			}
		}

		HttpResponse response = client.execute(get, context);
		this.cookies = response.getFirstHeader("Set-Cookie").toString();
		logger.info("Tentative réussie");

		BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
		StringBuffer result = new StringBuffer();
		String line = "";
		while ((line = rd.readLine()) != null) {
			result.append(line);
		}

		Document doc = Jsoup.parse(result.toString());
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
		logger.info("Contenu de type "+type+" chargés en mémoire : "+getContentListOnline());
	}

	public String getTargetSlug(String targetId, String type) throws ClientProtocolException, IOException {
		for (MetadataContent metadata : contentListOnline) {
			if (metadata.getId().equals(targetId) && metadata.getType().equalsIgnoreCase(type)) {
				return metadata.getSlug();
			}
		}
		return null;
	}

	public void downloaDraft(String targetId, String type) throws ClientProtocolException, IOException {
		String targetSlug = getTargetSlug(targetId, type);
		HttpGet get = new HttpGet(getDownloadDraftContentUrl(targetId, targetSlug));
		logger.debug("Tentative de téléchargement via le lien : "+getDownloadDraftContentUrl(targetId, targetSlug));


		HttpResponse response = client.execute(get);

		InputStream is = response.getEntity().getContent();
		String filePath = getOnlineContentPathDir() + File.separator + targetSlug + ".zip";
		FileOutputStream fos = new FileOutputStream(new File(filePath));

		int inByte;
		while ((inByte = is.read()) != -1)
			fos.write(inByte);
		is.close();
		fos.close();
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
    	InputStream input = MainApp.class.getClassLoader().getResourceAsStream("config.properties");

    	try {
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
}
