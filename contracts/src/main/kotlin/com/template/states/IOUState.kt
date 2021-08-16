package com.template.states

import com.template.contracts.TemplateContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import java.time.Instant

// *********
// * State *
// *********
@BelongsToContract(TemplateContract::class)
data class IOUState(val value: Int,
                    val lender: Party,
                    val borrower: Party,
                    val dueData: Instant,
                    val interest: Int,
                    val payment: Int = 0,
                    val status: String = "Created",
                    override val linearId: UniqueIdentifier = UniqueIdentifier()
                    ) : LinearState, QueryableState{
                        override val participants: List<AbstractParty>
                            get() = listOf(lender,borrower)

                    override fun generateMappedObject(schema: MappedSchema): PersistentState {
                       TODO("Not yet implemented")
                    }

                    override fun supportedSchemas(): Iterable<MappedSchema> {
                        TODO("Not yet implemented")
                    }
}