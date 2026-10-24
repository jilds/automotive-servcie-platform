/// JPA helpers for the `infrastructure` layer of every feature: the insert-or-update of an aggregate's entity
/// ([Upserts], [DomainEntity]) and the check for a named database constraint ([Constraints]). Never used by the
/// domain, application or api layers (`LayerRulesTests`).
@NamedInterface("persistence")
@NullMarked
package com.asp.platform.shared.persistence;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.NamedInterface;
