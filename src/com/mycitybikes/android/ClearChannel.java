package com.mycitybikes.android;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.CDATASection;
import org.w3c.dom.CharacterData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import com.mycitybikes.android.model.BikeStationStatus;
import com.mycitybikes.android.model.StationInfoBuilder;
import com.mycitybikes.android.model.StationLocation;
import com.mycitybikes.android.util.Utils;

/**
 * Class to perform ClearChannel specific bike station operations. We only
 * support Oslo so far.
 * 
 * @author jerome
 */
public class ClearChannel {

	public static void loadOsloBikeLocations(Context context,
			List<StationLocation> stationLocations) {
		try {
			AssetManager assets = context.getAssets();
			InputStream is = assets.open("oslo2.xml");
			loadStationLocationsAsset(is, stationLocations, "Oslo", "Norway");

		} catch (Exception e) {
			Log.e(Constants.TAG, "Failed to load Oslo bike station locations: "
					+ e.getMessage());
			e.printStackTrace();
		}
	}

	public static void loadStockholmBikeLocations(Context context,
			List<StationLocation> stationLocations) {
		try {
			AssetManager assets = context.getAssets();
			InputStream is = assets.open("stockholm.xml");
			loadStationLocationsAsset(is, stationLocations, "Stockholm",
					"Sweeden");

		} catch (Exception e) {
			Log.e(Constants.TAG, "Failed to load Stokholm bike station locations: "
							+ e.getMessage());
			e.printStackTrace();
		}
	}

	static void loadStationLocationsAsset(InputStream is,
			List<StationLocation> stationLocations, String city, String country)
			throws IOException {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db;
		Document dom;
		try {
			db = dbf.newDocumentBuilder();
			dom = db.parse(is);
		} catch (Exception e) {
			throw new IllegalStateException("Unexpected parsing issue.", e);
		}

		Node stationsNode = dom.getDocumentElement();
		if (!"stations".equals(stationsNode.getNodeName())) {
			throw new IllegalArgumentException("Unexpected XML:"
					+ stationsNode.getNodeName());
		}
		Integer id = null;
		String description = null;
		Double latitude = null;
		Double longitude = null;
		NodeList stationsChildren = stationsNode.getChildNodes();
		for (int i = 0; i < stationsChildren.getLength(); i++) {
			Node stationNode = stationsChildren.item(i);
			if (stationNode.getNodeType() != Element.ELEMENT_NODE) {
				continue;
			}
			if (!"station".equals(stationNode.getNodeName())) {
				throw new IllegalArgumentException("Unexpected XML:"
						+ stationNode.getNodeName());
			}
			NodeList stationChildren = stationNode.getChildNodes();
			for (int j = 0; j < stationChildren.getLength(); j++) {
				Node child = stationChildren.item(j);
				if (child.getNodeType() != Element.ELEMENT_NODE) {
					continue;
				}
				if ("description".equals(child.getNodeName())) {
					description = child.getFirstChild().getNodeValue().trim();
				} else if ("longitude".equals(child.getNodeName())) {
					longitude = new Double(child.getFirstChild().getNodeValue());
				} else if ("latitude".equals(child.getNodeName())) {
					latitude = new Double(child.getFirstChild().getNodeValue());
				} else if ("id".equals(child.getNodeName())) {
					id = new Integer(child.getFirstChild().getNodeValue());
				} else {
					throw new IllegalArgumentException(
							"Unexpected format of the XML station status "
									+ child.getNodeName());
				}
			}

			final StationLocation stationLocation = new StationLocation(id,
					city, country, description, longitude, latitude);
			stationLocation.setStationInfoBuilder(new StationInfoBuilder() {

				@Override
				public String buildStationInfo() {
					return ClearChannel.getStationInfo(stationLocation);
				}

			});
			stationLocations.add(stationLocation);
			Log.v(Constants.TAG, "loaded stationLocation: " + stationLocation);
		}
	}

	public static BikeStationStatus readBikeStationStatus(String httpUrl) {
		try {
			return parseStatus(Utils.readContent(httpUrl, 5000));
		} catch (Exception e) {
			throw new RuntimeException(
					"Unable to read/parse status information", e);
		}
	}

	static BikeStationStatus parseStatus(InputStream content)
			throws IOException {
		String s = Utils.parseISToString(content);
		s = s.substring(s.indexOf("<string"));

		int start = s.indexOf(">") + 1;
		int end = s.indexOf("</", start);
		String encodedContent = s.substring(start, end);
		String decodedContent = decode(encodedContent);

		Log.v(Constants.TAG, "decodedContent: " + decodedContent);

		InputStream is;
		try {
			is = new java.io.ByteArrayInputStream(decodedContent
					.getBytes("UTF-8"));
		} catch (UnsupportedEncodingException e) {
			throw new IllegalStateException("Unexpected issue.", e);
		}

		return parseBikeStationStatusDOM(is);
	}

	/*
	 * private static BikeStationStatus
	 * parseBikeStationStatusXstream(InputStream is) { XStream xstream = new
	 * XStream(new DomDriver()); xstream.alias("station",
	 * BikeStationStatus.class); xstream.aliasAttribute(BikeStationStatus.class,
	 * "readyBikes", "ready_bikes");
	 * xstream.aliasAttribute(BikeStationStatus.class, "emptyLocks",
	 * "empty_locks"); // xstream.addImplicitCollection(WikiItems.class,
	 * "entries");
	 * 
	 * BikeStationStatus bikeStationStatus = (BikeStationStatus)
	 * xstream.fromXML(is); return bikeStationStatus; }
	 */

	private static BikeStationStatus parseBikeStationStatusDOM(InputStream is) {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db;
		Document dom;
		try {
			db = dbf.newDocumentBuilder();
			dom = db.parse(is);
		} catch (Exception e) {
			throw new IllegalStateException("Unexpected parsing issue.", e);
		}

		Node stationNode = dom.getFirstChild();
		if (!"station".equals(stationNode.getNodeName())) {
			throw new IllegalArgumentException("Unexpected XML:"
					+ stationNode.getNodeName());
		}
		BikeStationStatus bikeStationStatus = new BikeStationStatus();
		NodeList stationChildren = stationNode.getChildNodes();
		for (int i = 0; i < stationChildren.getLength(); i++) {
			Node child = stationChildren.item(i);
			if ("description".equals(child.getNodeName())) {
				String description = child.getFirstChild().getNodeValue();
				int splitIndex = description.indexOf('-');
				if (splitIndex != -1) {
					description = description.substring(splitIndex + 1);
				}
				bikeStationStatus.setDescription(description);
			} else if ("longitute"/* sic... */.equals(child.getNodeName())) {
				bikeStationStatus.setLongitude(new Double(child.getFirstChild()
						.getNodeValue()));
			} else if ("latitude".equals(child.getNodeName())) {
				bikeStationStatus.setLatitude(new Double(child.getFirstChild()
						.getNodeValue()));
			} else if ("online".equals(child.getNodeName())) {
				bikeStationStatus.setOnline("1".equals(child.getFirstChild()
						.getNodeValue()));
			} else if ("empty_locks".equals(child.getNodeName())) {
				bikeStationStatus.setEmptyLocks(new Integer(child
						.getFirstChild().getNodeValue()));
			} else if ("ready_bikes".equals(child.getNodeName())) {
				bikeStationStatus.setReadyBikes(new Integer(child
						.getFirstChild().getNodeValue()));
			} else {
				throw new IllegalArgumentException(
						"Unexpected format of the XML station status "
								+ child.getNodeName());
			}
		}

		return bikeStationStatus;
	}

	public static String getStationInfo(StationLocation stationLocation) {
		if (stationLocation.getCity().equals("Oslo")) {
			return getOsloStationInfo(stationLocation.getId());
		} else if (stationLocation.getCity().equals("Stockholm")) {
			return stationLocation.getDescription()
					+ "\n\n(no live data for Stockholm)";
		} else {
			throw new IllegalStateException("" + stationLocation);
		}
	}

	public static String getOsloStationInfo(int stationIndex) {
		String result;
		try {
			BikeStationStatus status = readBikeStationStatus(stationIndex);
			result = formatStationInfo(status);
		} catch (Exception e) {
			result = "Error: station information not available";
		}
		return result;
	}

	private static String formatStationInfo(BikeStationStatus status) {
		String result;
		if (!status.isOnline()) {
			result = status.getDescription() + "\n\n(no station information)";
		} else {
			result = status.getDescription() + "\n\n" + status.getReadyBikes()
					+ " bike(s)\n" + status.getEmptyLocks() + " slot(s)";
		}
		return result;
	}

	public static BikeStationStatus readBikeStationStatus(int stationId) {
		return ClearChannel
				.readBikeStationStatus("http://smartbikeportal.clearchannel.no/public/mobapp/maq.asmx/getRack?id="
						+ stationId);
	}

	private static String decode(String content) {
		content = content.replaceAll("&lt;", "<");
		content = content.replaceAll("&gt;", ">");
		return content;
	}

	public static void loadBarcelonaBikeLocations(Context context,
			List<StationLocation> stationLocations) {
		try {
			loadBikeLocationsAndStatusFromKmlInPage(context, stationLocations,
					"http://www.bicing.com/localizaciones/localizaciones.php",
					"Barcelona", "Spain");

		} catch (Exception e) {
			Log.e(Constants.TAG, "Failed to load Barcelona bike station locations: "
							+ e.getMessage());
			e.printStackTrace();
		}
	}

	public static void loadBikeLocationsAndStatusFromKmlInPage(Context context,
			List<StationLocation> stationLocations, String httpUrl, String city, String country) {
		try {
			InputStream is = Utils.readContent(httpUrl, 5000);
			String kml = extractKMLFromHtml(is);
			Log.v(Constants.TAG, "Extracted KML: " + kml);
			InputStream is2 = new ByteArrayInputStream(kml.getBytes("UTF-8"));
			parseKml(is2, stationLocations, city, country);
		} catch (Exception e) {
			Log.e(Constants.TAG, "Failed to load " + city + "," + country
					+ " bike station locations: " + e.getMessage());
			e.printStackTrace();
		}
	}

	static void parseKml(InputStream is,
			List<StationLocation> stationLocations, String city, String country) {

		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db;
		Document dom;
		try {
			db = dbf.newDocumentBuilder();
			dom = db.parse(is);
		} catch (Exception e) {
			throw new IllegalStateException("Unexpected parsing issue.", e);
		}

		Node kmlNode = dom.getDocumentElement();
		if (!"kml".equals(kmlNode.getNodeName())) {
			throw new IllegalArgumentException("Unexpected XML:"
					+ kmlNode.getNodeName());
		}

		final BikeStationStatus bikeStationStatus = new BikeStationStatus();

		Integer id = null;
		String description = null;
		Double latitude = null;
		Double longitude = null;
		NodeList placemarks = dom.getElementsByTagName("Placemark");
		for (int i = 0; i < placemarks.getLength(); i++) {
			Node placemarkNode = placemarks.item(i);

			NodeList stationChildren = placemarkNode.getChildNodes();
			for (int j = 0; j < stationChildren.getLength(); j++) {
				Node child = stationChildren.item(j);
				if (child.getNodeType() != Element.ELEMENT_NODE) {
					continue;
				}
				if ("description".equals(child.getNodeName())) {
					CharacterData d = ((CharacterData) child.getFirstChild());
					CDATASection section = (CDATASection) child.getFirstChild();
					String data = section.getData();
					Pattern p = Pattern.compile("<div[^>]*><div[^>]*>(.*) - (.*)</div><div[^>]*>.*</div><div[^>]*>([0-9]+).*>([0-9]+).*</div></div>");
					Matcher m = p.matcher(data);
					if (m.matches() && m.groupCount() == 4) {
						id = new Integer(m.group(1));
						description = m.group(2);
						bikeStationStatus.setReadyBikes(new Integer(m.group(3)));
						bikeStationStatus.setEmptyLocks(new Integer(m.group(4)));
					} else {
						Log.e(Constants.TAG, "couldn't extract address from data:" + data);
					}
				} else if ("Point".equals(child.getNodeName())) {
					String coordinates = child.getFirstChild().getFirstChild().getNodeValue();
					String cood[] = coordinates.split(",");
					longitude = new Double(sanitizeCoordinates(cood[0]));
					latitude = new Double(sanitizeCoordinates(cood[1]));
					Double altitude = new Double(cood[2]);
				}
			}

			if (description == null || longitude == null || latitude == null) {
				Log.e(Constants.TAG, "couldn't find station location information. Skipping...");
				break;
			}
			final StationLocation stationLocation = new StationLocation(id,
					city, country, description, longitude, latitude);

			stationLocation.setStationInfoBuilder(new StationInfoBuilder() {

				@Override
				public String buildStationInfo() {
					return formatStationInfo(bikeStationStatus);
				}
			});
			stationLocations.add(stationLocation);
			Log.v(Constants.TAG, "loaded stationLocation: " + stationLocation);
		}
	}

	private static String sanitizeCoordinates(String coordinates) {
		return coordinates.replace("?", "");
	}

	static String extractKMLFromHtml(InputStream is) {
		BufferedReader r = new BufferedReader(new InputStreamReader(is));
		String line = null;
		try {
			while ((line = r.readLine()) != null) {
				int start = -1;
				if ((start = line.indexOf("<?xml version=\"1.0\" encoding=\"UTF-8\"?><kml")) >= 0) {
					int end = line.indexOf("</kml>");
					if (end == -1 || end < start) {
						throw new IllegalStateException("Unexpected HTML format. </kml> not found on same line as <kml>");
					}
					if (end < start) {
						throw new IllegalStateException("Unexpected HTML format. </kml> before <kml>");
					}
					return line.substring(start, end + "</kml>".length());
				}
			}
		} catch (IOException e) {
			throw new RuntimeException("Unable to extract KML", e);
		}
		Log.v(Constants.TAG, "kml: " + line);
		return line;
	}

}
