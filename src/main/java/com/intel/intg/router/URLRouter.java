package com.intel.intg.router;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mule.extension.http.api.HttpRequestAttributes;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class URLRouter {
	private String routerBasePath;
	private List<Map<String, Object>> routes = new ArrayList<Map<String, Object>>();
	final static Logger logger = LogManager.getLogger(URLRouter.class);

	public String getRouterBasePath() {
		return routerBasePath;
	}

	public void setRouterBasePath(String routerBasePath) {
		this.routerBasePath = routerBasePath;
	}

	// Constructor - called by Spring configuration at startup
	URLRouter() {
		// load the routes
		if (!loadRoutes()) {
			logger.error("Routes.json could not be loaded. See logs for details.");
		}
	}

	public Map<String, Object> getRoute(HttpRequestAttributes attributes) {
		if (this.routes == null) {
			logger.warn("routes could not be found - attempting to load...");
			if (!loadRoutes()) {
				logger.error("No routes could be loaded. Do you have a routes.json?");
			}
		}
		Map<String, Object> routeInfo = new HashMap<String, Object>();
		routeInfo.put("config", "");
		String requestPath = attributes.getRequestPath();
		String requestMethod = attributes.getMethod().toUpperCase();
		Boolean isPath = false;
		String retVal = "";
		String debugInfo;
		String[] requestSplit = null;
		String compareRequestPath = "";
		Map<String, String> routeVars = null;
		int pos = requestPath.indexOf(routerBasePath);
		if (pos != -1) {
			compareRequestPath = requestPath.substring(pos + routerBasePath.length(), requestPath.length());
			requestSplit = compareRequestPath.split("/");
		}
		routeInfo.put("basePath", routerBasePath);
		routeInfo.put("requestedRoute",  requestMethod.toUpperCase() + ":" + compareRequestPath);
		routeInfo.put("routeFlow", "");
		// Each Route
		for (Map<String, Object> tmpRoute : routes) {
			debugInfo= "Requested Route: [" + routeInfo.get("requestedRoute") + "], Available Route: [" + ((String)tmpRoute.get("method")).toUpperCase() + ":" + tmpRoute.get("route") + "]";
			routeVars = new HashMap<String, String>();
			compareRequestPath = "";

			// Route has matched, exit and return
			if (retVal.length() > 0) {
				break;
			}

			// Method check
			if ( ((String)tmpRoute.get("method")).equalsIgnoreCase(requestMethod) == false) {
				logger.debug(debugInfo + ", REJECTED: Request route method does not match defined route method. Request: [" + requestMethod + "] Defined Route: [" + (String)tmpRoute.get("method") + "]");
				continue;
			}

			String[] routeSplit = ((String) tmpRoute.get("route")).split("/");

			// The Requested path is longer than the route, and the route is not a wildcard,
			// exit.
			if ((requestSplit.length - 1 > routeSplit.length - 1)
					&& (routeSplit[routeSplit.length - 1].equals("*") == false)) {
				isPath = false;
				logger.debug(debugInfo + ", REJECTED: Request route pattern contains more parameters than the defined route pattern");
				continue;
			}

			// Each Route Param
			int ctr = -1;
			for (String routeParam : routeSplit) {
				ctr++;
				// Reached end of request, must be a failure, exit
				if (ctr > requestSplit.length - 1) {
					isPath = false;
					logger.debug(debugInfo + ", REJECTED: Defined route pattern contains more parameters than the request");
					break;
				}

				// Reached end of route, must be a match, exit
				if (routeParam.equals("*")) {
					isPath = true;
					break;
				}
				boolean isRouteVar = routeParam.startsWith("{") && routeParam.endsWith("}");
				if (isRouteVar) {
					routeVars.put(routeParam.substring(1, routeParam.length() - 1), requestSplit[ctr]);
				}
				if (!routeParam.equalsIgnoreCase(requestSplit[ctr]) && !isRouteVar) {
					isPath = false;
					logger.debug(debugInfo + ", REJECTED: Route & Pattern do not match");
					break; // Route match failed, go to next route
				}
				isPath = true;
			}
			if (isPath) {
				logger.debug(debugInfo + ", ACCEPTED: Method & Pattern Match");
				routeInfo.put("config", tmpRoute);
				// Mule 4 fix - common OAS path characters are not supported in Mule 4 flow names 
				routeInfo.put("routeFlow", ((String)tmpRoute.get("flowName")).replace("/","\\").replace("{", "(").replace("}", ")").replace(",", "|") + "_route"); 
				break;
			}
		}
		routeInfo.put("routeVars", routeVars);
		return routeInfo;
	}

	private boolean loadRoutes() {
		InputStream inputStream = null;
		ClassLoader classLoader = getClass().getClassLoader();
		ObjectMapper objectMapper = new ObjectMapper();
		boolean loadSuccess = true;
		List<String> resources=null;
		try {
			resources = getResourceFiles("/");
		} catch (IOException e1) {
			logger.error("Unable to scan classpath for routes!");
			return false;
		}
		File file;
		int versionFolderCount=0;
		
		for (String resource : resources) {
			file = new File(classLoader.getResource(resource).getFile());
			if (file.isDirectory()) {
				logger.debug("Scanning for 'routes.json' in resource folder '" + resource + "'...");
				String absolutePath = file.getAbsolutePath();
				try {
					inputStream = new FileInputStream( absolutePath + "/routes.json" );
					List<Map<String, Object>> tmpRoutes = objectMapper.readValue(inputStream, new TypeReference<List<Map<String, Object>>>() {});
					logger.info("Found " + tmpRoutes.size() + " routes defined in resource folder '" + resource + "'");
					versionFolderCount ++;
					this.routes.addAll(tmpRoutes);
				} catch (FileNotFoundException e) {
					logger.debug("Checking folder \"" + resource + "\".No 'routes.json' found.");
				} catch (IOException e) {
					logger.error("Could not load routes.json: " + e.getMessage());
				} finally {
					if (inputStream != null) {
						try {
							inputStream.close();
						} catch (IOException e) {
							logger.error(e.getMessage());
						}
					}
				}
			}
		}
		logger.info("Successfully retrieved " + this.routes.size() + " defined routes located in " + versionFolderCount + " project folders");
		return loadSuccess;
	}
	
	private List<String> getResourceFiles(String path) throws IOException {
	    List<String> filenames = new ArrayList<>();
	    try (
	            InputStream in = getResourceAsStream(path);
	            BufferedReader br = new BufferedReader(new InputStreamReader(in))
	            		) {
	        String resource;
	        while ((resource = br.readLine()) != null) {
	            filenames.add(resource);
	        }
	    }
	    logger.debug("Resources to search: " + filenames.toString());
	    return filenames;
	}
	
	private InputStream getResourceAsStream(String resource) {
	    final InputStream in = getContextClassLoader().getResourceAsStream(resource);
	    return in == null ? getClass().getResourceAsStream(resource) : in;
	}

	private ClassLoader getContextClassLoader() {
	    return Thread.currentThread().getContextClassLoader();
	}
	
	public List<String> validRoutes() {
		List<String> definedRoutes = new ArrayList<String>();
		for (Map<String,Object> routeConfig : this.routes ) {
			definedRoutes.add(((String)routeConfig.get("route")));
		}
		return definedRoutes;
	}
}