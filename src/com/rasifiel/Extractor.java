package com.rasifiel;
import jargs.gnu.CmdLineParser;
import jargs.gnu.CmdLineParser.IllegalOptionValueException;
import jargs.gnu.CmdLineParser.UnknownOptionException;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.util.Iterator;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.DomNode;
import com.gargoylesoftware.htmlunit.html.DomNodeList;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

public class Extractor {
	private static int minimumCountValue = 4;
	private static double tresholdValue = 0.8;
	static PrintWriter pw;

	private static void printUsage() {
		System.err
				.println("Usage: Extractor [{-o,--output} output_file] [{--mincnt} number] [{--treshold} number] url");
	}

	public static void main(String[] args)
			throws FailingHttpStatusCodeException, MalformedURLException,
			InterruptedException {
		System.getProperties().put(
				"org.apache.commons.logging.simplelog.defaultlog", "error");
		CmdLineParser parser = new CmdLineParser();
		CmdLineParser.Option output = parser.addStringOption('o', "output");
		CmdLineParser.Option treshold = parser.addDoubleOption("treshold");
		CmdLineParser.Option minimumCount = parser.addIntegerOption("mincnt");
		try {
			parser.parse(args);
		} catch (IllegalOptionValueException e) {
			System.err.println("Illegal option value: "
					+ e.getOption().longForm());
			printUsage();
			System.exit(1);
		} catch (UnknownOptionException e) {
			System.err.println("Unknown option: " + e.getOptionName());
			printUsage();
			System.exit(1);
		}
		minimumCountValue = (Integer) parser.getOptionValue(minimumCount, 4);
		tresholdValue = (Double) parser.getOptionValue(treshold, 0.8);
		String[] argl = parser.getRemainingArgs();
		if (argl.length < 1) {
			printUsage();
			System.exit(1);
		}
		String outputFile = (String) parser.getOptionValue(output);
		if (outputFile != null)
			try {
				pw = new PrintWriter(new File(outputFile));
			} catch (IOException e) {
				System.err.println("Can't create output file");
				System.exit(1);
			}
		else
			pw = new PrintWriter(System.out);
		WebClient wc = new WebClient();
		wc.setJavaScriptEnabled(false);
		wc.setCssEnabled(false);
		HtmlPage page = null;
		try {
			page = wc.getPage(argl[0]);
		} catch (IOException e) {
			System.err.println("URL can't be open");
			printUsage();
			System.exit(1);
		}
		HtmlElement body = page.getBody();
		deleteFalseElements(body);
		process(body);
		pw.println(bestBlockContent);
		pw.close();
	}

	private static WordsList garbageTags = new WordsList(new String[] {
			"input", "select", "script" });

	private static void deleteFalseElements(HtmlElement body) {
		if (garbageTags.contains(body.getTagName()))
			body.remove();
		else
			for (HtmlElement he : body.getChildElements())
				deleteFalseElements(he);
	}

	static int maxLength = 0;
	static String bestBlockContent = "";
	static double maxEquality = 0;

	private static void process(DomNode body) {
		DomNodeList<DomNode> be = body.getChildNodes();
		int textCnt = 0;
		for (DomNode dn : be)
			if (dn.getNodeType() == DomNode.TEXT_NODE
					|| badNodeName(dn.getNodeName()))
				textCnt++;
		int n = be.size();
		double sumEq = calcEquality(be, n);
		n -= textCnt;
		sumEq *= 2;
		sumEq += n;
		if (sumEq >= tresholdValue * n * n && n > minimumCountValue) {
			String xml = body.asXml();
			String text = body.asText();
			int size = getEffectiveSize(body);
			if (size > maxLength) {
				maxLength = text.length();
				bestBlockContent = xml;
				maxEquality = sumEq / n / n;
			}
		} else
			for (DomNode he : body.getChildren())
				process(he);
	}

	public static double calcEquality(DomNodeList<DomNode> be, int n) {
		double sumEq = 0;
		for (int i = 0; i < n; i++)
			if (!isBadNode(be.get(i)))
				for (int j = i + 1; j < n; j++)
					if (!isBadNode(be.get(j))) {
						double eq = compareNodeTree(be.get(i), be.get(j));
						sumEq += eq * eq;
					}
		return sumEq;
	}

	private static int getEffectiveSize(DomNode body) {
		if (body.getNodeType() == HtmlElement.TEXT_NODE)
			return body.getTextContent().length();
		if (garbageTags.contains(body.getNodeName()))
			return 0;
		int size = 0;
		for (DomNode dn : body.getChildren())
			size += getEffectiveSize(dn);
		return size;
	}

	private static double compareNodeTree(DomNode root1, DomNode root2) {
		if (!root1.getNodeName().equals(root2.getNodeName()))
			return 0;
		Iterator<DomNode> childIterator1 = root1.getChildren().iterator();
		Iterator<DomNode> childIterator2 = root2.getChildren().iterator();
		if (!childIterator1.hasNext() || !childIterator2.hasNext())
			return !childIterator1.hasNext() && !childIterator2.hasNext() ? 1.0
					: 0.0;
		DomNode currentNode1 = childIterator1.next();
		DomNode currentNode2 = childIterator2.next();
		int totalNodes = 0;
		double goodNodes = 0;
		while (true) {
			currentNode1 = skipBadNodes(childIterator1, currentNode1);
			currentNode2 = skipBadNodes(childIterator2, currentNode2);
			if ((!childIterator1.hasNext() && currentNode1 == null)
					|| (!childIterator2.hasNext() && currentNode2 == null))
				break;
			if (currentNode1 == null || currentNode2 == null)
				break;
			goodNodes += compareNodeTree(currentNode1, currentNode2);
			totalNodes++;
			if (!childIterator1.hasNext())
				break;
			currentNode1 = childIterator1.next();
			if (!childIterator2.hasNext())
				break;
			currentNode2 = childIterator2.next();
		}
		if (totalNodes == 0)
			return 1;
		return goodNodes / totalNodes;
	}

	public static DomNode skipBadNodes(Iterator<DomNode> iterator,
			DomNode currentNode) {
		while (isBadNode(currentNode)) {
			currentNode = null;
			if (!iterator.hasNext())
				break;
			currentNode = iterator.next();
		}
		return currentNode;
	}

	private static WordsList goodTags = new WordsList(new String[] { "div",
			"table", "tr", "tbody", "td", "a", "p" });

	private static boolean badNodeName(String nodeName) {
		return !goodTags.contains(nodeName);
	}

	private static boolean isBadNode(DomNode dn) {
		return dn.getNodeType() == DomNode.TEXT_NODE
				|| badNodeName(dn.getNodeName());
	}
}
