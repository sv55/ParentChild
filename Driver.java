import java.io.IOException;

import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.pdfbox.exceptions.CryptographyException;
import org.apache.pdfbox.exceptions.InvalidPasswordException;
import org.apache.tika.exception.TikaException;

class Driver {
	public static void main(String args[]) throws IOException, TikaException, CryptographyException, InvalidPasswordException, ParseException {
		Searcher searcher = new Searcher();
		searcher.addFileContents("/home/likewise-open/ZOHOCORP/vignesh-pt182/mail1/", "mail1", 20131211);
		searcher.addFileContents("/home/likewise-open/ZOHOCORP/vignesh-pt182/mail2/", "mail2", 20140111);
		searcher.addFileContents("/home/likewise-open/ZOHOCORP/vignesh-pt182/mail3/", "mail3", 20130211);
		searcher.searchFor();
	}
}