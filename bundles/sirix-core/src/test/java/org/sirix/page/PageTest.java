package org.sirix.page;

import net.openhft.chronicle.bytes.Bytes;
import org.sirix.Holder;
import org.sirix.XmlTestHelper;
import org.sirix.api.PageReadOnlyTrx;
import org.sirix.api.PageTrx;
import org.sirix.exception.SirixException;
import org.sirix.exception.SirixIOException;
import org.sirix.index.IndexType;
import org.sirix.io.bytepipe.ByteHandler;
import org.sirix.node.HashCountEntryNode;
import org.sirix.node.HashEntryNode;
import org.sirix.node.NodeKind;
import org.sirix.node.interfaces.DataRecord;
import org.sirix.page.interfaces.Page;
import org.sirix.settings.Constants;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.nio.ByteBuffer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertArrayEquals;

/**
 * Test class for all classes implementing the {@link Page} interface.
 *
 * @author Sebastian Graf, University of Konstanz
 * @author Johannes Lichtenberger, University of Konstanz
 */
public class PageTest {

  /**
   * {@link Holder} instance.
   */
  private Holder holder;

  /**
   * Sirix {@link PageReadOnlyTrx} instance.
   */
  private PageReadOnlyTrx pageReadTrx;

  @BeforeClass
  public void setUp() throws SirixException {
    XmlTestHelper.closeEverything();
    XmlTestHelper.deleteEverything();
    XmlTestHelper.createTestDocument();
    holder = Holder.generateDeweyIDResourceMgr();
    pageReadTrx = holder.getResourceManager().beginPageReadOnlyTrx();
  }

  @AfterClass
  public void tearDown() throws SirixException {
    pageReadTrx.close();
    holder.close();
  }

  @Test(dataProvider = "instantiatePages")
  public void testByteRepresentation(final Page[] handlers) {
    for (final Page handler : handlers) {
      final Bytes<ByteBuffer> data = Bytes.elasticByteBuffer();
      handler.serialize(pageReadTrx, data, SerializationType.DATA);
      final var pageBytes = data.toByteArray();
      final Page serializedPage =
          PageKind.getKind(handler.getClass()).deserializePage(pageReadTrx, data, SerializationType.DATA);
      serializedPage.serialize(pageReadTrx, data, SerializationType.DATA);
      final var serializedPageBytes = data.toByteArray();
      assertArrayEquals("Check for " + handler.getClass() + " failed.", pageBytes, serializedPageBytes);
    }
  }

  /**
   * Providing different implementations of the {@link Page} as Dataprovider to the test class.
   *
   * @return different classes of the {@link ByteHandler}
   * @throws SirixIOException if an I/O error occurs
   */
  @DataProvider(name = "instantiatePages")
  public Object[][] instantiatePages() throws SirixIOException {
    // IndirectPage setup.
    final IndirectPage indirectPage = new IndirectPage();
    // RevisionRootPage setup.
    // final RevisionRootPage revRootPage = new RevisionRootPage();

    // NodePage setup.
    final UnorderedKeyValuePage nodePage =
        new UnorderedKeyValuePage(XmlTestHelper.random.nextInt(Integer.MAX_VALUE), IndexType.DOCUMENT, pageReadTrx);
    for (int i = 0; i < Constants.NDP_NODE_COUNT - 1; i++) {
      final DataRecord record = XmlTestHelper.generateOne();
      nodePage.setRecord(record);
    }
    // NamePage setup.
    final NamePage namePage = new NamePage();
    final String name = new String(XmlTestHelper.generateRandomBytes(256));
    namePage.setName(name, NodeKind.ELEMENT, createPageTrxMock());

    // PathPage setup.
    final PathPage valuePage = new PathPage();

    // PathSummaryPage setup.
    final PathSummaryPage pathSummaryPage = new PathSummaryPage();

    return new Object[][] { { Page.class, new Page[] { indirectPage, namePage, valuePage, pathSummaryPage } } };
  }

  private PageTrx createPageTrxMock() {
    final var hashEntryNode = new HashEntryNode(2, 12, "name");
    final var hashCountEntryNode = new HashCountEntryNode(3, 1);

    final PageTrx pageTrx = mock(PageTrx.class);
    when(pageTrx.createRecord(any(HashEntryNode.class), eq(IndexType.NAME), eq(0))).thenReturn(hashEntryNode);
    when(pageTrx.createRecord(any(HashCountEntryNode.class), eq(IndexType.NAME), eq(0))).thenReturn(hashCountEntryNode);
    when(pageTrx.prepareRecordForModification(2L, IndexType.NAME, 0)).thenReturn(hashCountEntryNode);
    return pageTrx;
  }
}
