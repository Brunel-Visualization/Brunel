package org.brunel.util;

import java.util.ArrayList;
import java.util.List;

import org.brunel.action.Param;

public class Bidi {
	final private static char chLRM = '\u200E';
	final private static char chRLM = '\u200F';
	final private static char chLRE = '\u202A';
	final private static char chRLE = '\u202B';
	final private static char chPDF = '\u202C';
	final private static char chLRO = '\u202D';
	final private static char chRLO = '\u202D';
	
	final private static String strLRM = ""+'\u200E';
	final private static String strRLM = ""+'\u200F';
	final private static String strLRE = ""+'\u202A';
	final private static String strRLE = ""+'\u202B';
	//final private static String strPDF = ""+'\u202C';
	//final private static String strLRO = ""+'\u202D';
	//final private static String strRLO = ""+'\u202D';

	final public static ArrayList<Param> listLRE;
	final public static ArrayList<Param> listRLE;
	final public static ArrayList<Param> listPDF;
			
	static {
		listLRE = new ArrayList<>();
		listLRE.add(Param.makeString("" + chLRE));
		listLRE.add(Param.makeString("" + chLRM));
		
		listRLE = new ArrayList<>();
		listRLE.add(Param.makeString("" + chRLE));
		
		listPDF = new ArrayList<>();
		listPDF.add(Param.makeString("" + chLRM));
		listPDF.add(Param.makeString("" + chPDF));
	}


	public static String applyBidiParam(String src, String param) {
		return applyBidiParam(src, param, null);
	}

	public static StringBuffer applyBidiParamToQuoted(String src, String param) {
		char q = '\'';
		boolean quotes1 = (src.charAt(0) == q && src.charAt(src.length()-1) == q);
		StringBuffer result;
		if (quotes1)
			result =  new StringBuffer(src.substring(1, src.length()-1));
		else
			result = new StringBuffer(src);
		
		result = applyBidiParam(result, param);
		
		if (quotes1) {
			result.insert(0, q);
			result.append(q);
		}
		return result;
	}

	public static StringBuffer applyBidiParam(StringBuffer src, String param) {
		StringBuffer result = new StringBuffer();
		
		char firstCh = chLRE;

		if (param.equals("ltr")) {
		} else
			if (param.equals("rtl")) {
				firstCh = chRLE;
			} else 
				if(param.equals("auto")) {
					char ch;
					for (int i = 0; i < src.length(); i++) {
						ch = src.charAt(i);
						if (Character.getDirectionality(ch) == Character.DIRECTIONALITY_LEFT_TO_RIGHT) {
							firstCh = chLRE;
							break;
						}
						if (Character.getDirectionality(ch) == Character.DIRECTIONALITY_RIGHT_TO_LEFT) {
							firstCh = chRLE;
							break;
						}
					}

				} else {
					return src;
				}

		result.append(firstCh);
		result.append(firstCh == chLRE ?  chLRM : chRLM);
		result.append(src);
		result.append(firstCh == chLRE ?  chLRM : chRLM);

		result.append(chPDF);
		
		return result;

	}
	
	public static String applyBidiParam(String src, String param, String guidir) {		
		if (src == null || param == null)
			return src;
		if (src.isEmpty() || param.isEmpty())
			return src;
		if (src.charAt(src.length()-1) == chPDF)
			return src;
		if (src.length() > 1)
			if (src.charAt(src.length()-2) == chPDF)
				return src;

		StringBuffer result = new StringBuffer(src);

		if (param.equals("visual")) {
			result.insert(0, "rtl".equals(guidir) ? chRLO : chLRO);
			result.append(src);
			result.append(chPDF);
			return result.toString();
		} 
		
		result = applyBidiParam(result, param);

		return result.toString();
	}

	public static String[] applyBidiParam(String[] src, String param) {
		return applyBidiParam(src, param, null);
	}
	
	public static String[] applyBidiParam(String[] src, String param, String guidir) {
		for (int i = 0; i < src.length; i++) {
			src[i] = applyBidiParam(src[i], param, guidir);
		}
		return src;
	}

	public static String applyBidiParam(String src, Param param) {
		return applyBidiParam(src, param, null);
	}
	
	public static String applyBidiParam(String src, Param param, String guidir) {	
		return param == null? src : applyBidiParam(src, param.asString(), guidir);
	}

	public static String[] applyBidiParam(String[] src, Param param) {	
		return param == null? src : applyBidiParam(src, param.asString());
	}

	public static List<Param> applyBidiParam(List<Param> items, Param param) {
		return applyBidiParam(items, param, null);
	}

	public static List<Param> applyBidiParam(List<Param> items, Param bidiParam, String guidir) {
		return applyBidiParam(items, bidiParam.asString(), guidir);
	}

	public static List<Param> applyBidiParam(List<Param> items, String bidiParam, String guidir) {			
		if (items == null || bidiParam == null)
			return items;

		ArrayList<Param> result = new ArrayList<>();
		
		if ("ltr".equals(guidir)) {
			result.add(Param.makeString(strLRE));
			result.add(Param.makeString(strLRM));
		}

		if ("rtl".equals(guidir)) {
			result.add(Param.makeString(strRLE));
			result.add(Param.makeString(strRLM));
		}

		String str;
		Param newParam;
		for (int i = 0; i < items.size(); i++) {
			Param p = items.get(i);
			if (p.type() == Param.Type.string) {
				str = applyBidiParam(p.asString(), bidiParam, guidir);
				newParam = Param.makeString(str);
				newParam.addModifiers(p.modifiers());
				
				if ("ltr".equals(guidir)) {
					result.add(Param.makeString(strLRM));
				}
				if ("rtl".equals(guidir)) {
					result.add(Param.makeString(strRLM));
				}

				result.add(newParam);

				if ("ltr".equals(guidir)) {
					result.add(Param.makeString(strLRM));
				}
				if ("rtl".equals(guidir)) {
					result.add(Param.makeString(strRLM));
				}

			} else {
				result.add(p);
			}
		}

		if ("ltr".equals(guidir)) {
			result.add(Param.makeString(strLRM));
		}
		if ("rtl".equals(guidir)) {
			result.add(Param.makeString(strRLM));
		}

		if (guidir != null ) {
			result.add(Param.makeString("" + chPDF));
		}

		return result;
	}

}
