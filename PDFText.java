import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.exceptions.CryptographyException;
import org.apache.pdfbox.exceptions.InvalidPasswordException;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.util.PDFTextStripper;

/**
 * Gives the text in the given PDF file.
 */

public class PDFText {
	
	/**
	 * Returns the content of the PDF file as a string.
	 * @param file The input PDF file
	 * @return String
	 */
	
	public static String pdftoText(File file) {
		PDFParser parser;
		String parsedText = null;
		PDFTextStripper pdfStripper = null;
		PDDocument pdDoc = null;
		COSDocument cosDoc = null;
		try {
			parser = new PDFParser(new FileInputStream(file));
		} catch (IOException e) {
			System.err.println("Unable to open PDF Parser. " + e.getMessage());
			return null;
		}
		try {
			parser.parse();
			cosDoc = parser.getDocument();
			pdfStripper = new PDFTextStripper();
			pdDoc = new PDDocument(cosDoc);
			pdfStripper.setStartPage(1);
			pdfStripper.setEndPage(6);
			parsedText = pdfStripper.getText(pdDoc);
		} catch (Exception e) {
			System.err
					.println("An exception occured in parsing the PDF Document."
							+ e.getMessage());
		} finally {
			try {
				if (cosDoc != null)
					cosDoc.close();
				if (pdDoc != null)
					pdDoc.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return parsedText;
	}
	
	/**
	 * Loads the PDF documents, decrypts it.
	 * @param file The input PDF file
	 * @return String
	 * @throws IOException
	 * @throws CryptographyException
	 * @throws InvalidPasswordException
	 */
	
	public static String giveText(File file) throws IOException, CryptographyException, InvalidPasswordException {
		PDDocument document = PDDocument.load(file);
		if (document.isEncrypted())
		{
			document.decrypt("");
		}
		return pdftoText(file);
	}
}