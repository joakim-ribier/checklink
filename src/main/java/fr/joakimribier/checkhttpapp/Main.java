package fr.joakimribier.checkhttpapp;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.inject.Guice;
import com.google.inject.Injector;

import fr.joakimribier.checkhttpapp.exceptions.ConnectionServiceException;
import fr.joakimribier.checkhttpapp.exceptions.FileResourceServiceException;
import fr.joakimribier.checkhttpapp.guice.CheckHttpAppModule;
import fr.joakimribier.checkhttpapp.services.SendMailService;
import fr.joakimribier.checkhttpapp.utils.FileResourceService;

/**
 * CHKHTTP-URL is an application which check the accessibility of http url
 * 
 * The configuration file path default is defined in file.path
 * OR
 * It can be defined by argument ( args[0] )
 * 
 * 
 * Configuration file :
 * 
 * check_url=http://www.my-domain.com
 * smtp=host.smpt.com
 * username=from@my-domain.com
 * password=password
 * from=from@my-domain.com
 * to=to@my-domain.com
 * subject=It's the subject
 * text=Content plain text
 * smtp_debug=true
 * 
 * 
 * @author Joakim Ribier
 *
 */
public class Main {
	
	private final static Logger Logger = LoggerFactory.getLogger("application");
	private final static String DEFAULT_PATH = "file.path";
	
	public static void main(String[] args) {
		
		final Injector injector = Guice.createInjector(new CheckHttpAppModule());
		final FileResourceService fileResource = injector.getInstance(FileResourceService.class);
		final SendMailService sendMail = injector.getInstance(SendMailService.class);
		
		try {
			final String pathFileConfiguration = getArgsOrDefaultPathConfiguration(fileResource, args);
			if (Strings.isNullOrEmpty(pathFileConfiguration)) {
				Logger.info("path of configuration file is not defined - programme aborted");
				return;
			}
			
			final Properties properties = fileResource.load(pathFileConfiguration);
			final Configuration configuration = new Configuration.Builder().properties(properties).build();
			final String urlToCheck = configuration.getHttpUrlToCheck();
			int code = ifConnect(urlToCheck);
			if (code != HttpURLConnection.HTTP_OK) {
				Logger.info("check url " + urlToCheck + " failed: code = " + code);
				sendMail.send(
						configuration.getMailFrom(),
						configuration.getMailTo(),
						configuration.getMailSubject(),
						formatContentText(configuration.getMaiText(), urlToCheck, code),
						configuration.getSMTPHost(),
						configuration.getSMTPUser(),
						configuration.getSMTPPassword(),
						configuration.getSMTPDebug());
			} else {
				Logger.info("check url {} success", urlToCheck);
			}
			
		} catch (Exception e) {
			Logger.error(e.getMessage(), e);
		}
    }

	private static String getArgsOrDefaultPathConfiguration(FileResourceService fileUtils,
			String[] args) throws FileResourceServiceException {
		
		if (args.length == 1) {
			return args[0];
		} else {
			return fileUtils.getContent(DEFAULT_PATH);
		}
	}
	
	private static int ifConnect(String url) throws ConnectionServiceException {
		try {
			URL myURL = new URL(url);
			HttpURLConnection myURLConnection = (HttpURLConnection) myURL.openConnection();
			myURLConnection.connect();
			return myURLConnection.getResponseCode();
		} catch (MalformedURLException e) {
			throw new ConnectionServiceException(e.getMessage(), e);
		} catch (IOException e) {
			return HttpURLConnection.HTTP_NOT_FOUND;
		}
	}

	private static String getDateTime() {
		return DateTimeFormat
				.forPattern("d MMMM, yyyy 'à' HH:mm:ss")
				.print(new DateTime());
	}
	
	private static String formatContentText(String text, String urlToChecked, int code) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append(getDateTime()).append("\r\n\r\n");
		stringBuilder.append(urlToChecked).append(" - ").append("CODE: ").append(code);
		stringBuilder.append("\r\n\r\n").append(text);
		return stringBuilder.toString();
	}
}