package samrock.manga.maneger;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import samrock.manga.Chapters.Chapter;
import samrock.manga.Manga;
import samrock.manga.MinimalManga;

public class MangaManeger {
	private static volatile boolean init = false;
	private static IMangaManeger instance;

	public static void init() throws Exception  {
		synchronized (MangaManeger.class) {
			if(init)
				throw new IllegalStateException("already initialized");
			init0(new MangaManegerImpl());
		}
	}
	public static void init(IMangaManeger manager) {
		synchronized (MangaManeger.class) {
			if(init)
				throw new IllegalStateException("already initialized");
			init0(manager);
		}
	}
	private static void init0(IMangaManeger manager) {
		instance = manager;
		init = true;
	}
	public static int getMangasCount() {
		return instance.getMangasCount();
	}
	public static void loadMostRecentManga() {
		instance.loadMostRecentManga();
	}
	public static Mangas mangas() {
		return instance.mangas();
	}
	public static TagsDAO tagsDao() {
		return instance.tagsDao();
	}
	public static void addMangaToDeleteQueue(Manga manga) {
		instance.addMangaToDeleteQueue(manga);
	}
	public static ThumbManager getThumbManager() {
		return instance.getThumbManager();
	}
	public static Manga getCurrentManga() {
		return instance.getCurrentManga();
	}
	static MinimalManga getMinimalManga(int mangaId) throws SQLException, IOException {
		return instance.getMinimalManga(mangaId);
	}
	public static List<Chapter> getChapters(Manga manga) throws Exception {
		return instance.getChapters(manga);
	}
	public static List<String> getUrls(Manga manga) throws Exception {
		return instance.getUrls(manga);
	}
	public static int mangaIdOf(MinimalManga manga) {
		return instance.mangaIdOf(manga);
	}
	public static int indexOf(MinimalManga manga) {
		return instance.indexOf(manga);
	}
	static int indexOfMangaId(int manga_id) {
		return instance.indexOfMangaId(manga_id);
	}
	static List<Chapter> loadChapters(IndexedManga manga) throws IOException, SQLException  {
		return instance.loadChapters(manga);
	}
	static List<Chapter> reloadChapters(IndexedManga manga, List<Chapter> loadedChapters) throws IOException, SQLException {
		return instance.reloadChapters(manga, loadedChapters);
	}
	public static SearchManeger searchManager(boolean create) {
		return instance.searchManager(create);
	}
	public static RecentsDao recentsDao() {
		return instance.recentsDao();
	}
}
