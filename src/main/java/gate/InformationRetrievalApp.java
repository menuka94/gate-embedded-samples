package gate;

/*
 *  InformationRetrievalApp.java
 *
 *  Copyright (c) 1998-2003, The University of Sheffield.
 *
 *  This file is part of GATE (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Library General Public License,
 *  Version 2, June 1991 (in the distribution as file licence.html,
 *  and also available at http://gate.ac.uk/gate/licence.html).
 *
 *  Marin Dimitrov, 27/Jan/2003
 *
 *  $Id: InformationRetrievalApp.java,v 1.4 2004/12/14 14:36:24 niraj Exp $
 */

import java.net.*;
import java.util.*;

import gate.*;
import gate.util.Err;
import gate.util.Out;

import gate.util.GateException;
import gate.creole.ResourceInstantiationException;
import gate.creole.ir.*;
import gate.creole.ir.lucene.*;

public class InformationRetrievalApp {

	private static String INDEX_LOCATION = "c:/temp/lucene";

	//NOTE: the above path should NOT be URL style!
	private static String SERIAL_DATASTORE_PATH = "file:///c:/temp/gate_corpus";
	//the index folder must be EMPTY

	public InformationRetrievalApp() {
	}

	public static void main(String[] args) {

		InformationRetrievalApp irApp = new InformationRetrievalApp();

		//init GATE
		//    this is the first thing to be done
		try {
			Gate.init();
			Out.prln("GATE initialised...");
		}
		catch (GateException gex) {
			Err.prln("cannot initialise GATE...");
			gex.printStackTrace();
			return;
		}

		try {
			//1. create and open a serial data store
			DataStore sds = Factory.createDataStore("gate.persist.SerialDataStore", SERIAL_DATASTORE_PATH);
			sds.open();

			//2. create transient corpus with 2 docs
			Corpus corpus = irApp.createTestCorpus();

			//3. serialize corpus in Serial datastore
			Corpus serialCorpus = (Corpus) sds.adopt(corpus, null);

			//4. sync datastore
			sds.sync(serialCorpus);

			//5. index the serialized corpus
			IndexedCorpus indexedCorpus = (IndexedCorpus) serialCorpus;

			//5.1. create Index definition that tells the IR engine how to index the corpus
			DefaultIndexDefinition did = new DefaultIndexDefinition();
			//5.1. use the Lucene IR engine (the only option at present)
			//    may be changed in the future
			did.setIrEngineClassName(gate.creole.ir.lucene.LuceneIREngine.class.getName());
			//5.2. specify index location - this is different from the location of the serialized corpus
			did.setIndexLocation(INDEX_LOCATION);
			//5.3. specify fields to be indexed and their respective Readers
			//    if the field being indexed is document feature then set NULL as the default
			//    FieldReader would search the document features for a feature with the same name and will
			//    index its value (expected String)
			did.addIndexField(new IndexField("author", null, false));
			//    for the document content specify a predefined FieldReader called DocumentCOntentReader
			//    that will index the content of the document
			did.addIndexField(new IndexField("content", new DocumentContentReader(), false));
			//    any other things to be indexed such as custom annotations and their features
			//    ...require a custom FieldReader to be created and spceified in the same manner

			//5.4 finally tell the indexed corpus (that will be created) to use the above
			//    index definition
			indexedCorpus.setIndexDefinition(did);

			//5.5 ask the IndexManager to create the index (delete it beforehand if already existing)
			indexedCorpus.getIndexManager().deleteIndex();
			indexedCorpus.getIndexManager().createIndex();

			//now we have the two documents indexed and FTS queries may be specified for them
			//    using the respective IR manager (Lucene) search syntax

			//6. (optionally) optimize index
			//    with time indexes become suboptimal with additions/removal of new documents
			//    optimize the index from time to time for better performance
			//    on large indexes this will take some time since it involves index recreation
			indexedCorpus.getIndexManager().optimizeIndex();

			//7. search in index

			//7.2.  create the proper Search subclass
			//    since we're using the Lucene IR engine, use LuceneSearch
			Search search = new LuceneSearch();
			//    the Search instance needs to know which corpus to search
			search.setCorpus(indexedCorpus);
			//    ...and the query to be performed
			String query = "+content:\"until there's a cure\" +author:foundation";
			//    ...this query looks for documents that has "author" field equal to "foundation"
			//    and contain the phrase "until there's a cure" in the content (the until.org page used
			//    for document2 is such)

			//7.3 execute query
			QueryResultList res = search.search(query);
			//7.4    ...and get results
			Iterator it = res.getQueryResults();
			//7.5 show results
			while (it.hasNext()) {
				QueryResult qr = (QueryResult) it.next();
				float score = qr.getScore();
				//the resultset contains (doc_id, relevance) pairs
				//    in order to get the real document, the corpus shoudl be used
				Document resultDoc = (Document) sds.getLr("gate.corpora.DocumentImpl", qr.getDocumentID());
				Out.prln("Query1: DOC_NAME=" + resultDoc.getName());
				Out.prln("Query1: score = " + score);
				Out.prln("Query1: author = " + resultDoc.getFeatures().get("author"));
				Out.prln("------------");
				//we expect just one document printed
			}

			//8. execute a second query
			String query2 = "+author:foundation";
			//    ...this query looks for documents that has "author" field equal to "foundation"

			//8.1 execute query
			QueryResultList res2 = search.search(query2);
			//8.2    ...and get results
			Iterator it2 = res2.getQueryResults();
			//8.3 show results
			while (it2.hasNext()) {
				QueryResult qr = (QueryResult) it2.next();
				float score = qr.getScore();
				//the resultset contains (doc_id, relevance) pairs
				//    in order to get the real document, the corpus shoudl be used
				Document resultDoc = (Document) sds.getLr("gate.corpora.DocumentImpl", qr.getDocumentID());
				Out.prln("Query2: DOC_NAME=" + resultDoc.getName());
				Out.prln("Query2: score = " + score);
				Out.prln("Query2: author = " + resultDoc.getFeatures().get("author"));
				Out.prln("------------");
				//we expect two documents printed
			}

			Out.prln("done...");

		}
		catch (Exception ex) {
			ex.printStackTrace(Err.getPrintWriter());
		}

	}

	public Corpus createTestCorpus()
			throws MalformedURLException, ResourceInstantiationException {

		Document doc1 = Factory.newDocument(new URL("http://www.wish.org/"));
		//add a dummy feature that will be indexed
		doc1.getFeatures().put("author", "Make-A-Wish Foundation");
		doc1.setName("Make-A-Wish document");
		//Document doc1 = Factory.newDocument(new URL("file:///c:/temp/test.txt"));

		Document doc2 = Factory.newDocument(new URL("http://www.until.org"));
		//add a dummy feature that will be indexed
		doc2.getFeatures().put("author", "Until There's A Cure Foundation");
		doc2.setName("until.org document");

		assert doc1 != null && doc2 != null;

		// create a corpus with the above documents
		Corpus result = Factory.newCorpus("test corpus");
		assert result != null;
		result.add(doc1);
		result.add(doc2);

		return result;
	}

}
