package com.example.fucknotes

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.fucknotes.data.db.AppDatabase
import com.example.fucknotes.data.repo.NotesRepository
import com.example.fucknotes.media.MediaStore
import com.example.fucknotes.media.PptxImporter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A deck built by hand — one slide with an inherited title, a filled rectangle and a picture —
 * comes in as a one-page notebook whose page shows all three. Isolated database; no owner data.
 */
@RunWith(AndroidJUnit4::class)
class PptxImportTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    private val repo = NotesRepository(db)
    private val files = mutableListOf<File>()
    private val notes = mutableListOf<Long>()

    @After fun cleanup() = runBlocking {
        repo.deleteForever(notes)
        files.forEach { it.delete() }
        db.close()
    }

    @Test fun deckBecomesANotebookPageWithTitleShapeAndPicture(): Unit = runBlocking {
        val dir = File(context.cacheDir, "pptx-test-${UUID.randomUUID()}").apply { mkdirs() }
        val deck = File(dir, "Lecture deck.pptx").also(files::add)
        writeDeck(deck)

        val id = PptxImporter(context, repo).import(Uri.fromFile(deck), null).also(notes::add)
        val note = requireNotNull(repo.getNote(id))
        assertEquals("Lecture deck", note.title)
        assertEquals(1, note.pageCount)
        val page = repo.observeAttachments(id).first().single()
        assertEquals(0, page.pageIndex)

        val bitmap = MediaStore.decodeImage(File(page.path))
        try {
            // The PDF importer renders 960 x 540 pt at 3 px/pt.
            val scale = bitmap.width / 960f
            fun px(xPt: Float, yPt: Float) = bitmap.getPixel((xPt * scale).toInt(), (yPt * scale).toInt())
            assertEquals("background stays white", Color.WHITE, px(20f, 500f))
            assertTrue("rectangle fill is red", Color.red(px(240f, 240f)) > 200 && Color.green(px(240f, 240f)) < 60)
            assertTrue("picture is green", Color.green(px(720f, 240f)) > 200 && Color.red(px(720f, 240f)) < 60)
            var dark = 0
            for (x in 120 until 840 step 2) for (y in 40 until 130 step 2) {
                val c = px(x.toFloat(), y.toFloat())
                if (Color.red(c) + Color.green(c) + Color.blue(c) < 300) dark++
            }
            assertTrue("title text was drawn from the layout placeholder", dark > 50)
        } finally { bitmap.recycle() }
        dir.deleteRecursively()
        Unit
    }

    @Test fun legacyBinaryDeckIsRefusedWithAdvice(): Unit = runBlocking {
        val dir = File(context.cacheDir, "ppt-test-${UUID.randomUUID()}").apply { mkdirs() }
        val old = File(dir, "old.ppt").also(files::add)
        old.writeBytes(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte()) + ByteArray(64))
        try {
            PptxImporter(context, repo).import(Uri.fromFile(old), null)
            fail("A .ppt must be refused")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains(".pptx"))
        }
        dir.deleteRecursively()
        Unit
    }

    private fun writeDeck(target: File) {
        val image = ByteArrayOutputStream().also { out ->
            Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
                .compress(Bitmap.CompressFormat.PNG, 100, out)
        }.toByteArray()
        ZipOutputStream(target.outputStream()).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray()); zip.closeEntry()
            }
            entry("ppt/presentation.xml", """
                <p:presentation xmlns:p="$P" xmlns:r="$R" xmlns:a="$A">
                  <p:sldMasterIdLst><p:sldMasterId id="2147483648" r:id="rId2"/></p:sldMasterIdLst>
                  <p:sldIdLst><p:sldId id="256" r:id="rId1"/></p:sldIdLst>
                  <p:sldSz cx="12192000" cy="6858000"/>
                </p:presentation>""")
            entry("ppt/_rels/presentation.xml.rels", """
                <Relationships xmlns="$PR">
                  <Relationship Id="rId1" Type="$OD/slide" Target="slides/slide1.xml"/>
                  <Relationship Id="rId2" Type="$OD/slideMaster" Target="slideMasters/slideMaster1.xml"/>
                </Relationships>""")
            entry("ppt/slides/slide1.xml", """
                <p:sld xmlns:p="$P" xmlns:a="$A" xmlns:r="$R"><p:cSld><p:spTree>
                  <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/>
                  <p:sp><p:nvSpPr><p:cNvPr id="2" name="Title"/><p:cNvSpPr/><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr>
                    <p:spPr/>
                    <p:txBody><a:bodyPr/><a:p><a:r><a:rPr lang="en"/><a:t>Database caching strategies</a:t></a:r></a:p></p:txBody></p:sp>
                  <p:sp><p:nvSpPr><p:cNvPr id="3" name="Box"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
                    <p:spPr><a:xfrm><a:off x="1524000" y="2286000"/><a:ext cx="3048000" cy="1524000"/></a:xfrm>
                      <a:prstGeom prst="rect"><a:avLst/></a:prstGeom><a:solidFill><a:srgbClr val="FF0000"/></a:solidFill></p:spPr></p:sp>
                  <p:pic><p:nvPicPr><p:cNvPr id="4" name="Picture"/><p:cNvPicPr/><p:nvPr/></p:nvPicPr>
                    <p:blipFill><a:blip r:embed="rId2"/><a:stretch><a:fillRect/></a:stretch></p:blipFill>
                    <p:spPr><a:xfrm><a:off x="7620000" y="2286000"/><a:ext cx="3048000" cy="1524000"/></a:xfrm><a:prstGeom prst="rect"/></p:spPr></p:pic>
                </p:spTree></p:cSld></p:sld>""")
            entry("ppt/slides/_rels/slide1.xml.rels", """
                <Relationships xmlns="$PR">
                  <Relationship Id="rId1" Type="$OD/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
                  <Relationship Id="rId2" Type="$OD/image" Target="../media/image1.png"/>
                </Relationships>""")
            entry("ppt/slideLayouts/slideLayout1.xml", """
                <p:sldLayout xmlns:p="$P" xmlns:a="$A"><p:cSld><p:spTree>
                  <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/>
                  <p:sp><p:nvSpPr><p:cNvPr id="2" name="Title"/><p:cNvSpPr/><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr>
                    <p:spPr><a:xfrm><a:off x="1524000" y="508000"/><a:ext cx="9144000" cy="1270000"/></a:xfrm></p:spPr>
                    <p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:endParaRPr/></a:p></p:txBody></p:sp>
                </p:spTree></p:cSld></p:sldLayout>""")
            entry("ppt/slideLayouts/_rels/slideLayout1.xml.rels", """
                <Relationships xmlns="$PR"><Relationship Id="rId1" Type="$OD/slideMaster" Target="../slideMasters/slideMaster1.xml"/></Relationships>""")
            entry("ppt/slideMasters/slideMaster1.xml", """
                <p:sldMaster xmlns:p="$P" xmlns:a="$A"><p:cSld><p:bg><p:bgPr><a:solidFill><a:schemeClr val="bg1"/></a:solidFill></p:bgPr></p:bg>
                  <p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld>
                  <p:clrMap bg1="lt1" tx1="dk1" bg2="lt2" tx2="dk2" accent1="accent1" accent2="accent2" accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6" hlink="hlink" folHlink="folHlink"/>
                  <p:txStyles>
                    <p:titleStyle><a:lvl1pPr algn="l"><a:defRPr sz="4400" b="1"><a:solidFill><a:schemeClr val="tx1"/></a:solidFill></a:defRPr></a:lvl1pPr></p:titleStyle>
                    <p:bodyStyle><a:lvl1pPr marL="342900" indent="-342900"><a:buChar char="•"/><a:defRPr sz="2800"/></a:lvl1pPr></p:bodyStyle>
                    <p:otherStyle><a:lvl1pPr><a:defRPr sz="1800"/></a:lvl1pPr></p:otherStyle>
                  </p:txStyles></p:sldMaster>""")
            entry("ppt/slideMasters/_rels/slideMaster1.xml.rels", """
                <Relationships xmlns="$PR"><Relationship Id="rId1" Type="$OD/theme" Target="../theme/theme1.xml"/></Relationships>""")
            entry("ppt/theme/theme1.xml", """
                <a:theme xmlns:a="$A" name="T"><a:themeElements><a:clrScheme name="Office">
                  <a:dk1><a:sysClr val="windowText" lastClr="000000"/></a:dk1><a:lt1><a:sysClr val="window" lastClr="FFFFFF"/></a:lt1>
                  <a:dk2><a:srgbClr val="44546A"/></a:dk2><a:lt2><a:srgbClr val="E7E6E6"/></a:lt2>
                  <a:accent1><a:srgbClr val="4472C4"/></a:accent1><a:accent2><a:srgbClr val="ED7D31"/></a:accent2>
                  <a:accent3><a:srgbClr val="A5A5A5"/></a:accent3><a:accent4><a:srgbClr val="FFC000"/></a:accent4>
                  <a:accent5><a:srgbClr val="5B9BD5"/></a:accent5><a:accent6><a:srgbClr val="70AD47"/></a:accent6>
                  <a:hlink><a:srgbClr val="0563C1"/></a:hlink><a:folHlink><a:srgbClr val="954F72"/></a:folHlink>
                </a:clrScheme></a:themeElements></a:theme>""")
            zip.putNextEntry(ZipEntry("ppt/media/image1.png")); zip.write(image); zip.closeEntry()
        }
    }

    private companion object {
        const val P = "http://schemas.openxmlformats.org/presentationml/2006/main"
        const val A = "http://schemas.openxmlformats.org/drawingml/2006/main"
        const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        const val PR = "http://schemas.openxmlformats.org/package/2006/relationships"
        const val OD = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    }
}
