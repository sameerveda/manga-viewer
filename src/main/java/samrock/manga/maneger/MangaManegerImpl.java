package samrock.manga.maneger;
import static sam.manga.samrock.mangas.MangasMeta.LAST_READ_TIME;
import static sam.manga.samrock.mangas.MangasMeta.MANGAS_TABLE_NAME;
import static sam.sql.ResultSetHelper.getInt;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import sam.logging.MyLoggerFactory;
import sam.manga.samrock.chapters.ChaptersMeta;
import sam.manga.samrock.mangas.MangaUtils;
import sam.manga.samrock.meta.RecentsMeta;
import sam.manga.samrock.urls.nnew.UrlsMeta;
import sam.manga.samrock.urls.nnew.UrlsPrefixImpl;
import sam.myutils.Checker;
import sam.nopkg.Junk;
import sam.reference.ReferenceUtils;
import samrock.manga.Chapters.Chapter;
import samrock.manga.Manga;
import samrock.manga.MinimalManga;
import samrock.manga.recents.ChapterSavePoint;
import samrock.manga.recents.MinimalChapterSavePoint;
import samrock.utils.Utils;
final class MangaManegerImpl implements IMangaManeger {
	private static final Logger logger = MyLoggerFactory.logger(MangaManeger.class);

	/**
	 * Array Indices of mangas currently showing on display
	 */
	private final MangasOnDisplay mangasOnDisplay;
	private final ThumbManager thumbManager;
	private MangasDAO mangas;
	private RecentChapterDao recents;
	private TagsDAO tags;

	private IndexedManga currentManga;
	private final AtomicBoolean stopping = new AtomicBoolean(false);

	public MangaManegerImpl() throws SQLException, InstantiationException, IllegalAccessException, ClassNotFoundException, IOException {
		mangas = new MangasDAO();
		mangasOnDisplay = new MangasOnDisplay(mangas.getMangaIds());
		thumbManager = new ThumbManager();

		Utils.addExitTasks(() -> {
			if(stopping.get())
				return;

			stopping.set(true);
			mangasOnDisplay.close();

			//TODO close everything
			try {
				unloadManga(currentManga);
				mangas.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
	}
	@Override
	public Manga getCurrentManga() {
		return currentManga;
	}
	private static final Object LOAD_MOST_RECENT_MANGA = new Object();
	private static final Object LOAD_MANGA = new Object();

	/**
	 * load corresponding manga, ChapterSavePoint and set to currentManga and currentSavePoint  
	 * @param arrayIndex
	 */
	private Manga loadManga(Object loadType, MinimalManga manga) {
		if(loadType != LOAD_MOST_RECENT_MANGA && manga == null)
			throw new NullPointerException("manga == null");

		if(currentManga == manga)
			return (Manga) manga;
		if(manga != null && manga instanceof Manga)
			return (Manga) manga;

		if(loadType == LOAD_MOST_RECENT_MANGA && currentManga != null && currentManga.getLastReadTime() > Utils.START_UP_TIME)
			return currentManga;

		try {
			DB db = dao.samrock();
			unloadManga(currentManga);

			if(loadType == LOAD_MOST_RECENT_MANGA)
				manga = dao.getMinimalManga(db.executeQuery("SELECT "+RecentsMeta.MANGA_ID+" FROM "+RecentsMeta.TABLE_NAME+" WHERE "+RecentsMeta.TIME+" = (SELECT MAX("+LAST_READ_TIME+") FROM "+MANGAS_TABLE_NAME+")", getInt(RecentsMeta.MANGA_ID)));
			else if(loadType != LOAD_MANGA)
				throw new IllegalStateException("unknonwn loadType: "+loadType);

			currentManga = dao.getFullManga((IndexedMinimalManga) manga);
			currentSavePoint = dao.getFullSavePoint(currentManga);
			return currentManga;
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "error while loading full manga: "+manga, e);
			return null;
		}
	}

	@Override
	public void  loadMostRecentManga(){
		loadManga(LOAD_MOST_RECENT_MANGA, null);
	}

	private Set<Integer> deleteChapters;

	private void unloadManga(Manga mm) throws SQLException {
		if(mm == null)
			return;

		IndexedManga m = (IndexedManga) mm;
		List<Integer> deletedChapIds = m.getDeletedChaptersIds();
		if(Checker.isNotEmpty(deletedChapIds)) {
			if(deleteChapters == null)
				deleteChapters = new HashSet<>();
			deleteChapters.addAll(deletedChapIds);
			deletedChapIds.clear();
		}

		dao.saveSavePoint(currentSavePoint);
		dao.saveManga(m);

		if(!stopping.get())
			mangasOnDisplay.update(m, currentSavePoint);
	}
	
	/**
	 * 
	 * @return a <b>copy</b> of mangasOnDisplay
	 */
	@Override
	public MangasOnDisplay getMangasOnDisplay() {
		return mangasOnDisplay;
	}
	@Override
	public MinimalChapterSavePoint getChapterSavePoint(MinimalManga manga) {
		return dao.getSavePoint(manga);
	}
	@Override
	public DB samrock() {
		return dao.samrock();
	}
	@Override
	public TagsDAO getTagDao() {
		return dao.tagsDAO();
	}
	@Override
	public void addMangaToDeleteQueue(Manga manga) {
		mangasOnDisplay.getDeleteQueue().add(manga);
	}
	@Override
	public ThumbManager getThumbManager() {
		return thumbManager;
	}
	@Override
	public MinimalManga getMinimalManga(int manga_id) {
		return dao.getMinimalManga(manga_id);
	}

	private final String CHAPTERS_SELECT = "SELECT * FROM "+ChaptersMeta.CHAPTERS_TABLE_NAME+ " WHERE "+ChaptersMeta.MANGA_ID + " = ";

	@Override
	public List<Chapter> getChapters(Manga m) throws SQLException {
		Objects.requireNonNull(m);
		IndexedManga manga = (IndexedManga)m;
		return dao.samrock().collectToList(CHAPTERS_SELECT.concat(String.valueOf(mangaIdOf(manga))), manga::newChapter);
	}

	// manga_id -> urls 
	private final HashMap<Integer, Object> urlsMap = new HashMap<>();
	private SoftReference<UrlsPrefixImpl[]> prefixesList;

	private static final String URL_SELECT = DB.selectAll(UrlsMeta.URLSUFFIX_TABLE_NAME) + " WHERE "+UrlsMeta.MANGA_ID+" = ";

	@Override
	public List<String> getUrls(MinimalManga manga) throws SQLException {
		Object obj = urlsMap.get(mangaIdOf(manga));
		if(obj != null) {
			if(obj instanceof List)
				return (List<String>) obj;

			List<String> list = (List<String>) ReferenceUtils.get((WeakReference)obj);
			if(list != null)
				return list;
		}

		UrlsPrefixImpl[] m = ReferenceUtils.get(prefixesList);

		if(m == null) {
			prefixesList = new SoftReference<>(m = DB.mangaUrlsPrefixes().values().toArray(new UrlsPrefixImpl[0]));
			logger.fine(() -> "LOADED: "+UrlsPrefixImpl.class);
		}

		UrlsPrefixImpl[] ma = m;
		List<String> urls = DB.executeQuery(URL_SELECT.concat(Integer.toString(mangaIdOf(manga))),
				rs -> {
					ArrayList<String> list = new ArrayList<>();

					for (UrlsPrefixImpl u : ma) {
						String s = u.resolve(u.getColumnName()); 
						if(s != null)
							list.add(s);
					}

					if(list.isEmpty())
						return Collections.emptyList();
					else {
						list.trimToSize();
						return Collections.unmodifiableList(list);
					}
				});

		if(urls.isEmpty())
			urlsMap.put(mangaIdOf(manga), urls);
		else 
			urlsMap.put(mangaIdOf(manga), new WeakReference<>(urls));

		logger.fine(() -> "Urls loaded: ("+urls.size()+"), manga_id: "+mangaIdOf(manga)); 
		return urls;
	}
	
	@Override
	public List<Chapter> loadChapters(IndexedManga manga) {
		// TODO Auto-generated method stub
		return Junk.notYetImplemented();
	}
	@Override
	public List<Chapter> reloadChapters(IndexedManga manga, List<Chapter> loadedChapters) {
		// TODO Auto-generated method stub
		return Junk.notYetImplemented();
	}
	@Override
	public int indexOfMangaId(int manga_id) {
		return mangas.indexOfMangaId(manga_id);
	}
}