package cz.koto.misak.dbshowcase.android.mobile.model;

import java.util.List;

import cz.koto.misak.dbshowcase.android.mobile.DbApplication;
import cz.koto.misak.dbshowcase.android.mobile.api.DbShowcaseAPIClient;
import cz.koto.misak.dbshowcase.android.mobile.model.utility.SchoolModelComposer;
import cz.koto.misak.dbshowcase.android.mobile.persistence.PersistenceSyncState;
import cz.koto.misak.dbshowcase.android.mobile.persistence.PersistenceType;
import cz.koto.misak.dbshowcase.android.mobile.persistence.preference.SettingsStorage;
import cz.koto.misak.dbshowcase.android.mobile.persistence.realm.ShowcaseRealmModule;
import io.reactivex.Maybe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;


public class ModelProvider extends SettingsStorage {

	private static ModelProvider sInstance;

	private final SchoolModel mSchoolModel = new SchoolModel();
	ShowcaseRealmModule mRealmModule = DbApplication.get().getDbComponent().provideShowcaseRealmLoadModule();
	private PersistenceType mPersistenceType;
	private PersistenceSyncState mPersistenceSyncState;


	public ModelProvider() {
		super();
		mPersistenceSyncState = super.getActivePersistenceSyncState();
		mPersistenceType = super.getActivePersistenceType();
	}


	public static ModelProvider get() {
		if(sInstance == null)
			sInstance = new ModelProvider();
		return sInstance;
	}


	@Override
	public PersistenceSyncState getActivePersistenceSyncState() {
		return mPersistenceSyncState;
	}


	@Override
	protected void setActivePersistenceSyncState(PersistenceSyncState persistenceSyncState) {
		super.setActivePersistenceSyncState(persistenceSyncState);
		mPersistenceSyncState = persistenceSyncState;
	}


	@Override
	public PersistenceType getActivePersistenceType() {
		return mPersistenceType;
	}


	@Override
	protected void setActivePersistenceType(PersistenceType persistenceType) {
		super.setActivePersistenceType(persistenceType);
		mPersistenceType = persistenceType;
	}


	public void loadModel(DataHandlerListener successListener) {
		switch(mPersistenceType) {
			case REALM:
				mSchoolModel.addSchoolItems(mRealmModule.getSchoolClass());
				successListener.handleSuccess();
				break;
			case DB_FLOW:
			case NONE:
			default:
				successListener.handleFailed(new RuntimeException("Unsupported persistence type:" + mPersistenceType));
		}
	}


	public SchoolModel getSchoolModel() {
		return mSchoolModel;
	}


	public final void updateModelFromApi(DataHandlerListener resultListener) {
		Maybe.zip(DbShowcaseAPIClient.getAPIService().realmClassList(),
				DbShowcaseAPIClient.getAPIService().realmTeacherList(),
				DbShowcaseAPIClient.getAPIService().realmStudentList(),
				SchoolModelComposer::composeSchoolModel)
				.subscribeOn(Schedulers.io())
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(list -> {
							setSchoolModel(list, resultListener);
						},
						throwable -> {
							setActivePersistenceSyncState(PersistenceSyncState.ERROR);
							resultListener.handleFailed(throwable);
						});
	}


	private void setSchoolModel(List<? extends SchoolClassInterface> schoolModelItems, DataHandlerListener outerDataHandlerListener) {
		switch(mPersistenceSyncState) {
			case ERROR:
			case ENABLED:
				switch(mPersistenceType) {
					case REALM:
						mRealmModule.saveOrUpdateSchoolClass(schoolModelItems, new DataHandlerListener() {
							@Override
							public void handleSuccess() {
								mSchoolModel.addSchoolItems(schoolModelItems);
								outerDataHandlerListener.handleSuccess();
							}


							@Override
							public void handleFailed(Throwable throwable) {
								setActivePersistenceSyncState(PersistenceSyncState.ERROR);
								mSchoolModel.addSchoolItems(schoolModelItems);
								outerDataHandlerListener.handleFailed(throwable);
							}
						});
						setActivePersistenceSyncState(PersistenceSyncState.ENABLED);
						break;
					case DB_FLOW:
					default:
						mSchoolModel.addSchoolItems(schoolModelItems);
				}
				break;
			case DISABLED:
				mSchoolModel.addSchoolItems(schoolModelItems);
		}

	}


	private void updateModelFromRealm() {
		//loadni model z realmu (+ umozni na plusko load z API + pri zapnute synchronizaci ukladej load z API do realmu).
	}
}