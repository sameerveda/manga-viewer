package samrock.manga;

import static sam.manga.samrock.mangas.MangasMeta.BU_ID;
import static sam.manga.samrock.mangas.MangasMeta.CATEGORIES;
import static sam.manga.samrock.mangas.MangasMeta.DESCRIPTION;
import static sam.manga.samrock.mangas.MangasMeta.DIR_NAME;
import static sam.manga.samrock.mangas.MangasMeta.LAST_READ_TIME;
import static sam.manga.samrock.mangas.MangasMeta.LAST_UPDATE_TIME;
import static sam.manga.samrock.mangas.MangasMeta.STARTUP_VIEW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import sam.config.MyConfig;
import sam.logging.MyLoggerFactory;
import samrock.manga.Chapters.Chapter;
import samrock.manga.recents.ChapterSavePoint;
import samrock.utils.Views;

public abstract class Manga extends MinimalListManga implements Iterable<Chapter> {
	private static final Logger LOGGER = MyLoggerFactory.logger(Manga.class);

	//constants
	private final int buId;
	private final String dirName;
	private final String description;
	private final long last_update_time;
	private final String tags;

	private long lastReadTime;
	private Views startupView;
	
	private final Chapters chapters;
	private final Path mangaFolder;

	public Manga(ResultSet rs, int version) throws SQLException {
		super(rs, version);

		buId = rs.getInt(BU_ID);
		dirName = rs.getString(DIR_NAME);
		description = rs.getString(DESCRIPTION);
		lastReadTime = rs.getLong(LAST_READ_TIME);
		last_update_time = rs.getLong(LAST_UPDATE_TIME); 
		startupView = Views.parse(rs.getString(STARTUP_VIEW));
		tags = rs.getString(CATEGORIES);
		Path p = Paths.get(MyConfig.MANGA_DIR, dirName);
		mangaFolder = Files.exists(p) ? p : null;
		
		this.chapters = new Chapters(this, rs);
	}
	public String getDirName() { return dirName; }
	public String[] getTags() { return parseTags(tags); }
	public int getBuId() { return buId; }

	public Views getStartupView() {return startupView;}
	public Path getMangaFolderPath() {return mangaFolder;}
	public long getLastReadTime() {return lastReadTime;}
	public void setLastReadTime(long lastReadTime) {modified(); this.lastReadTime = lastReadTime;}
	public long getLastUpdateTime() {return last_update_time;}

	void setStartupView(Views startupView) {
		modified();
		if(startupView.equals(Views.CHAPTERS_LIST_VIEW) || startupView.equals(Views.DATA_VIEW))
			this.startupView = startupView;
	}
	
	@Override
	public ChapterSavePoint getSavePoint() {
		return (ChapterSavePoint) super.getSavePoint();
	}

	protected abstract String[] parseTags(String tags);
	@Override
	protected abstract ChapterSavePoint loadSavePoint() ;
	protected abstract List<Chapter> loadChapters() throws Exception;
	protected abstract List<Chapter> reloadChapters(List<Chapter> existingChapters) throws Exception;

	public String getDescription(){
		return description;
	}
	public void resetChapters() {
		if(chapters.reload()) {
			modified();
			chapters.resetCounts();  
			LOGGER.fine(() -> "chapter reset manga_id: "+ manga_id);
		}
	}
	
	@Override
	public Iterator<Chapter> iterator() {
		return chapters.iterator();
	}
	
	@Override
	public int getReadCount() {
		return chapters.read_count;
	}
	@Override
	public int getUnreadCount() {
		return chapters.unread_count;
	}
	public Chapters getChapters() {
		return chapters;
	}
	protected void onDeleteChapter(Chapter c) { 
		
	}
	public Chapter _newChapter(ResultSet rs) throws SQLException {
		return chapters._newChapter(rs);
	}
}
