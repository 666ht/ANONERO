/*
 * Copyright (c) 2018 m2049r
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.anonero.model

import kotlinx.serialization.Serializable
import java.util.regex.Pattern

@Serializable
class Subaddress(
    private val accountIndex: Int,
    val addressIndex: Int,
    val address: String,
    var label: String
) : Comparable<Subaddress> {
    var amount: Long = 0

    override fun compareTo(other: Subaddress): Int { // newer is <
        val compareAccountIndex = other.accountIndex - accountIndex
        return if (compareAccountIndex == 0) other.addressIndex - addressIndex else compareAccountIndex
    }

    val squashedAddress: String
        get() = address.substring(0, 8) + "…" + address.substring(address.length - 8)

    val displayLabel: String
        get() {
            val normalizedLabel = label.trim()
            return (when {
                normalizedLabel.isEmpty() || DEFAULT_LABEL_FORMATTER.matcher(normalizedLabel).matches() ->
                    if (addressIndex == 0) "主地址 0" else "子地址 $addressIndex"
                DEFAULT_SUBADDRESS_LABEL_FORMATTER.matcher(normalizedLabel).matches() ->
                    "子地址 $addressIndex"
                DEFAULT_PRIMARY_LABEL_FORMATTER.matcher(normalizedLabel).matches() ->
                    "主地址 0"
                else -> label
            }).replace("#", "")
        }

    val displayLabelWithIndex: String
        get() {
            val normalizedLabel = label.trim()
            val isCustomLabel = normalizedLabel.isNotEmpty() &&
                !DEFAULT_LABEL_FORMATTER.matcher(normalizedLabel).matches() &&
                !DEFAULT_SUBADDRESS_LABEL_FORMATTER.matcher(normalizedLabel).matches() &&
                !DEFAULT_PRIMARY_LABEL_FORMATTER.matcher(normalizedLabel).matches()
            return if (isCustomLabel) normalizedLabel else displayLabel
        }

    companion object {
        val DEFAULT_LABEL_FORMATTER: Pattern =
            Pattern.compile("^[0-9]{4}-[0-9]{2}-[0-9]{2}-[0-9]{2}:[0-9]{2}:[0-9]{2}$")
        val DEFAULT_SUBADDRESS_LABEL_FORMATTER: Pattern =
            Pattern.compile("^Subaddress(?:\\s*#?\\s*[0-9]+)?$")
        val DEFAULT_PRIMARY_LABEL_FORMATTER: Pattern =
            Pattern.compile("(?i)^Primary\\s+address(?:\\s*#?\\s*0)?$")
    }

    val totalAmount: Long
        get() = amount
}
