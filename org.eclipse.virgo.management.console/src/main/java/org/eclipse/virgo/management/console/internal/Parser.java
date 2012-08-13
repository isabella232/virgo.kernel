/*******************************************************************************
 * Copyright (c) 2008, 2011 VMware Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   VMware Inc. - initial contribution
 *******************************************************************************/
package org.eclipse.virgo.management.console.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.Scanner;

/**
 * @author cgfrost
 * 
 */
public class Parser {

	private static final String START_DECLARATION = "<!--@";

	private static final String END_DECLARATION = "@-->";

	private static final char IMPORT = '&'; // Replace the declaration with another file that will also be parsed, value can be a lookup.

	private static final char SET = '>'; //Place the given value in to the page context, 'String:String'.
	
	private static final char LOOKUP = '<'; // Replace the declaration with a lookup from the page context.

	private static final char EVALUATE = '?'; // Replace the declaration with a the result of an evaluation.
	
	private static final char START_LOOP = '['; //Start a loop and keep looping while the given value is true.
	
	private static final char END_LOOP = ']'; //End of a loop.

	private final Map<String, String> pageContext;

	private final PrintWriter out;

	private final ContentURLFetcher urlFetcher;

	/**
	 * 
	 * @param out
	 * @param modelData
	 */
	public Parser(PrintWriter out, ContentURLFetcher urlFetcher, Map<String, String> modelData) {
		this.out = out;
		this.urlFetcher = urlFetcher;
		this.pageContext = modelData;
	}

	/**
	 * 
	 * @param in
	 */
	public final void parse(InputStream in) throws IOException {
		Scanner scanner = new Scanner(in);
		String parsedLine;
		while (scanner.hasNextLine()) {
			parsedLine = parseLine(scanner.nextLine());
			if(parsedLine != null && !parsedLine.trim().isEmpty()){
				out.append(parsedLine);
				out.append('\n');
			}
		}
	}

	private String parseLine(String line) throws IOException {
		int startOffset = line.indexOf(START_DECLARATION);
		if(startOffset > -1){
			int endOffset = line.indexOf(END_DECLARATION);
			if(endOffset  > startOffset){
				String arg = line.substring(startOffset + START_DECLARATION.length() + 1, endOffset);
				String newValue = "";
				switch (line.charAt(startOffset + START_DECLARATION.length())) {
				case IMPORT:
					newValue = doImport(arg);
					break;
				case SET:
					newValue = doSet(arg);
					break;
				case LOOKUP:
					newValue = doLookup(arg);
					break;
				case EVALUATE:
					newValue = doEvaluate(arg);
					break;
				case START_LOOP:
					newValue = doStartLoop(arg);
					break;
				case END_LOOP:
					newValue = doEndLoop(arg);
					break;
				default:
					//Unknown command, do nothing
					break;
				}
				line = String.format("%s%s%s", line.substring(0, startOffset), newValue, line.substring(endOffset + END_DECLARATION.length()));
				line = parseLine(line);// There might be further declarations in the line.
			}
		}
		return line;
	}

	private String doImport(String arg) throws IOException {
		arg = resolveField(arg);
		if(arg.charAt(0) != '/'){
			arg = '/' + arg;
		}
		URL content = this.urlFetcher.getRequestedContentURL(arg);
		URLConnection resourceConn = content.openConnection();
		InputStream in = resourceConn.getInputStream();
		try {
			this.parse(in);
		} finally {
			in.close();
		}
		return "";
	}
	
	private String doSet(String arg) {
		int splitOffSet = arg.indexOf(':');
		if(splitOffSet >= 0){
			this.pageContext.put(arg.substring(0, splitOffSet), arg.substring(splitOffSet + 1));
		}
		return "";
	}

	private String doLookup(String arg){
		String newValue = this.pageContext.get(arg);
		if(newValue == null){
			return "";
		}
		return newValue;
	}

	private String doEvaluate(String arg) {
		int equalsOffset = arg.indexOf('=');
		int thenOffset = arg.indexOf(':');
		int elseOffset = arg.indexOf(':', thenOffset + 1);
		
		boolean result;
		if(equalsOffset >= 0){
			String query = resolveField(arg.substring(0, equalsOffset));
			String comparator = resolveField(arg.substring(equalsOffset + 1, thenOffset));
			result = query.equals(comparator);
		} else {
			String query = resolveField(arg.substring(0, thenOffset));
			result = Boolean.valueOf(query);

		}
		if(result){
			return resolveField(arg.substring(thenOffset + 1, elseOffset));
		} else {
			return ((elseOffset + 1) == arg.length()) ? "" : resolveField(arg.substring(elseOffset + 1));
		}
	}

	private String doStartLoop(String arg) {
		return "";
	}

	private String doEndLoop(String arg) {
		return "";
	}
	
	private String resolveField(String field){
		if(field.charAt(0) == LOOKUP){
			field = doLookup(field.substring(1));
		}
		return field;
	}

}
