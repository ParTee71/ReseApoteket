package se.partee71.reseapoteket.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import se.partee71.reseapoteket.data.medicines.AssetMedicineRepository
import se.partee71.reseapoteket.data.medicines.MedicineRepository
import se.partee71.reseapoteket.data.repository.DefaultFamilyItemRepository
import se.partee71.reseapoteket.data.repository.DefaultHouseholdRepository
import se.partee71.reseapoteket.data.repository.DefaultPackingRepository
import se.partee71.reseapoteket.data.repository.DefaultPersonRepository
import se.partee71.reseapoteket.data.repository.DefaultRegimenRepository
import se.partee71.reseapoteket.data.repository.DefaultTripRepository
import se.partee71.reseapoteket.data.repository.FamilyItemRepository
import se.partee71.reseapoteket.data.repository.HouseholdRepository
import se.partee71.reseapoteket.data.repository.PackingRepository
import se.partee71.reseapoteket.data.repository.PersonRepository
import se.partee71.reseapoteket.data.repository.RegimenRepository
import se.partee71.reseapoteket.data.repository.TripRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun households(repository: DefaultHouseholdRepository): HouseholdRepository

    @Binds abstract fun persons(repository: DefaultPersonRepository): PersonRepository

    @Binds abstract fun regimens(repository: DefaultRegimenRepository): RegimenRepository

    @Binds abstract fun familyItems(repository: DefaultFamilyItemRepository): FamilyItemRepository

    @Binds abstract fun trips(repository: DefaultTripRepository): TripRepository

    @Binds abstract fun packing(repository: DefaultPackingRepository): PackingRepository

    @Binds abstract fun medicines(repository: AssetMedicineRepository): MedicineRepository
}
