import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.ReaderUtil;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryWrapperFilter;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.search.grouping.GroupDocs;
import org.apache.lucene.search.grouping.TopGroups;
import org.apache.lucene.search.join.FixedBitSetCachingWrapperFilter;
import org.apache.lucene.search.join.ScoreMode;
import org.apache.lucene.search.join.ToParentBlockJoinCollector;
import org.apache.lucene.search.join.ToParentBlockJoinQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.FixedBitSet;
import org.apache.lucene.util.Version;
import org.apache.pdfbox.exceptions.CryptographyException;
import org.apache.pdfbox.exceptions.InvalidPasswordException;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
public class Searcher {

	private WhitespaceAnalyzer analyzer;
	private Directory directory;
	private IndexWriter writer;
	private IndexReader reader;
	private IndexSearcher searcher;

	public Searcher() throws IOException, TikaException, CryptographyException, InvalidPasswordException {
		analyzer = new WhitespaceAnalyzer(Version.LUCENE_46);
		directory = new RAMDirectory();
		writer = new IndexWriter(directory, new IndexWriterConfig(Version.LUCENE_46, analyzer));
	}

	private Document addMail(String mailID, int date) {
		Document mail = new Document();
		mail.add(new StringField("docType", "mail", Field.Store.NO));
		mail.add(new StringField("mid", mailID, Field.Store.YES));
		mail.add(new IntField("date", date, Field.Store.NO));
		return mail;
	}

	private Document addAttachment(String atchID, String content, int size) {
		Document att = new Document();
		FieldType type = new FieldType();
		type.setIndexed(true);
		type.setIndexOptions(FieldInfo.IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
		type.setStored(false);
		type.setStoreTermVectors(true);
		type.setTokenized(true);
		type.setStoreTermVectorOffsets(true);
		att.add(new StringField("aid", atchID, Field.Store.YES));
		att.add(new Field("attcontent", content, type));
		att.add(new IntField("size", size, Field.Store.NO));
		return att;
	}

	/*private Document getMail(Filter parents, int childDocID) throws IOException {
		final List<AtomicReaderContext> leaves = reader.leaves();
		final int subIndex = ReaderUtil.subIndex(childDocID, leaves);
		final AtomicReaderContext leaf = leaves.get(subIndex);
		final FixedBitSet bits = (FixedBitSet)parents.getDocIdSet(leaf, null);
		return leaf.reader().document(bits.nextSetBit(childDocID - leaf.docBase));
	}*/

	public void addFileContents(String folderPath, String key, int date) throws IOException, TikaException, CryptographyException, InvalidPasswordException {
		File folder = new File(folderPath);
		File[] files = folder.listFiles();
		List<Document> docs = new ArrayList<Document>();
		for(int i = 0; i < files.length; ++i) {
			String fileName = files[i].getName();
			System.out.println("Indexing " + fileName + "..." );
			Tika tika = new Tika();
			StringBuilder currentContent = new StringBuilder();
			if(fileName.contains(".pdf")) {
				currentContent.append(PDFText.giveText(files[i]).toLowerCase());
			} else {
				currentContent.append(tika.parseToString(files[i]).toLowerCase());
			}
			docs.add(addAttachment(fileName, currentContent.toString(), currentContent.length()));
		}
		docs.add(addMail(key, date));
		writer.addDocuments(docs);
		System.out.println("****************************************************");
	}

	private void findHits(ArrayList<Query> cQuery, ArrayList<Query> pQuery) throws IOException {
		writer.close();
		reader = DirectoryReader.open(directory);
		searcher = new IndexSearcher(reader);
		
		Filter parentsFilter = new FixedBitSetCachingWrapperFilter(new QueryWrapperFilter(new TermQuery(new Term("docType", "mail"))));
		BooleanQuery childQuery = new BooleanQuery();
		for(Query query : cQuery) {
			childQuery.add(new BooleanClause(query, Occur.MUST));
		}
		BooleanQuery parentQuery = new BooleanQuery();
		for(Query query : pQuery) {
			parentQuery.add(new BooleanClause(query, Occur.MUST));
		}
		ToParentBlockJoinQuery childJoinQuery = new ToParentBlockJoinQuery(childQuery, parentsFilter, ScoreMode.Avg);
		BooleanQuery fullQuery = new BooleanQuery();
		fullQuery.add(new BooleanClause(childJoinQuery, Occur.MUST));
		if(pQuery.size() == 0)
			fullQuery.add(new BooleanClause(new MatchAllDocsQuery(), Occur.MUST));
		else
			fullQuery.add(new BooleanClause(parentQuery, Occur.MUST));
		ToParentBlockJoinCollector c = new ToParentBlockJoinCollector(Sort.RELEVANCE, 20, true, true);
		searcher.search(fullQuery, c);
		TopGroups<Integer> results = c.getTopGroups(childJoinQuery, null, 0, 10, 0, true);
		if(results == null) {
			System.out.println("No Matching Documents");
			return;
		}
		for(int i = 0; i < results.groups.length; ++i) {
			GroupDocs<Integer> group = results.groups[i];
			Document parentDoc = searcher.doc(group.groupValue);
			for(int j = 0; j < group.scoreDocs.length; ++j) {
				Document childDoc = searcher.doc(group.scoreDocs[j].doc);
				System.out.println(parentDoc.get("mid") + " - " + childDoc.get("aid"));
			}
		}
	}
	
	public void searchFor() throws IOException, ParseException {
		ArrayList<Query> cQuery = new ArrayList<Query>();
		ArrayList<Query> pQuery = new ArrayList<Query>();
		PhraseQuery query = new PhraseQuery();
		query.add(new Term("attcontent", "vignesh"));
		query.add(new Term("attcontent", "sivakumar"));
		query.setSlop(1);
		cQuery.add(query);
		TermQuery t2 = new TermQuery(new Term("attcontent", "aadhar"));
		WildcardQuery t1 = new WildcardQuery(new Term("attcontent", "vasant*"));
		cQuery.add(t1);
		cQuery.add(t2);
		NumericRangeQuery<Integer> q1 = NumericRangeQuery.newIntRange("size", 3000, 7000, true, true);
		NumericRangeQuery<Integer> q2 = NumericRangeQuery.newIntRange("date", 20131201, 20131231, true, true);
		//cQuery.add(q1);
		pQuery.add(q2);
		findHits(cQuery, pQuery);
	}
}
