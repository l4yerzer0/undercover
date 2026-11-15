package com.undercover.vpn.parser

import com.undercover.vpn.shared.parser.happ.HappDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HappDecoderTest {

    @Test
    fun testUnwrapAddPrefix() {
        val wrapped = "happ://add/https%3A%2F%2Fexample.com%2Fsubscription"
        val unwrapped = HappDecoder.unwrap(wrapped)
        assertEquals("https://example.com/subscription", unwrapped)
    }

    @Test
    fun testUnwrapV2RayTunImportPrefix() {
        val wrapped = "v2raytun://import/https%3A%2F%2Fexample.com%2Fv2sub"
        val unwrapped = HappDecoder.unwrap(wrapped)
        assertEquals("https://example.com/v2sub", unwrapped)
    }

    @Test
    fun testUnwrapEmbeddedHttpLink() {
        val botLink = "https://t.me/myvpn_bot?start=happ%3A%2F%2Fcrypt5%2Ftest"
        val unwrapped = HappDecoder.unwrap(botLink)
        assertEquals("happ://crypt5/test", unwrapped)
    }

    @Test
    fun testIsHappOrV2Link() {
        assertTrue(HappDecoder.isHappOrV2Link("happ://crypt/abc"))
        assertTrue(HappDecoder.isHappOrV2Link("happ://crypt5/abc"))
        assertTrue(HappDecoder.isHappOrV2Link("happ://add/vless://xyz"))
        assertTrue(HappDecoder.isHappOrV2Link("v2raytun://crypt/abc"))
        assertTrue(HappDecoder.isHappOrV2Link("v2raytun://import/https://example.com"))
        assertTrue(HappDecoder.isHappOrV2Link("incy://add/vless://xyz"))
    }

    @Test
    fun testDecryptCrypt1() {
        val link = "happ://crypt/l9GixptwG4eeiwhf4yRPHO9LFmSKpYy4jmaCh5lpI8HiT7I0PY2lEN+RPz6NEZHfua842V3GNngQtjVf3R8BRaXEUTrdJOVFHyLZSkstNCL/3hmkJvMNp3Cyv2QLdndQmmNU3wYtWle5ffXcNGNrYHSOJcWTLbvRfavEJvFDHDA="
        val decrypted = HappDecoder.decrypt(link)
        assertNotNull(decrypted)
        assertEquals("https://example.com/test_crypt1", decrypted)
    }

    @Test
    fun testDecryptCrypt2() {
        val link = "happ://crypt2/HebHmIcPUdeAFndeYQYlgR458QCHWgn6oiQleLXXoVjh85YFwaJTXTXnd9hjxbp6b8MA4kr6JMaCqEaHCRyKvXOMWMfIt3VxiKPJz9GIcZgZJ/sagOW0FV6ThJFjnqEfWBhxYSClIT98A1spULzkFOvfGRjnDSfSNegzcSLkqYpd2UToUG5ie0e6FqXUHHrXcULR9oUfkADTuK5BNH4WtjMODotEsaMYKsvr8VmqeSfiqT1LVqtqYX2yRkh6nxbEMg9vkFgMZNQlzSw3cJoO8S0z0+UP6wuizGSJ+uK8ZWf/ih0P95lFSP9pR8F0OxHTNuVN4ADFwtRbPwO2aDR34AB2NCntaildmQGmF8O6j9LZHO6k9yK3UZdRrJ0VX1AqnhEnzmdaM4CqSatcw1Z3HAHrNe07tU/H3AkPhmrgfKAcsDWJiXeX15oYHwoheth8XR1MIjggzdLYTeD3OOUBEiw45KyWmTgnpReWfGpcx5m6f22YWdetAAyUFyqqoZ9glB3SJI91lQHbpeokK8CIapo54x6UBDnuocq0wKS7w7L3Z4HlAtjiwlBj9o4c+YpWt60+q73CniZW1M+taxX2oxg9DSxaUYqceD0HvAPY4QYn/hloRqpWEAZmYg11L7JxlPcRPe/c553nDX3yobYA3TZ2tdlhTC5sXZbCnOiPtx4="
        val decrypted = HappDecoder.decrypt(link)
        assertNotNull(decrypted)
        assertEquals("https://example.com/test_crypt2", decrypted)
    }

    @Test
    fun testDecryptCrypt3() {
        val link = "happ://crypt3/jq9OCYAC8Z1/XbpWxeV3ZFCq5ZvKlcAviSRrFygQUhNQ7QS2AAJIQH65cYsJ26DVczyk0OxDN66fQzxWfs/WQayBS1GO4NWCweknjpPNCYJN8i2EL6Ym3YqBTi1mj/Z3HIiIP5wUByXYYqZ6bWb6hesMWBdX6a2IDbx+pdcFWRm+D2X4xt/ytFH0mBd+G24ZyhgNK/SF+IWCjgCB74ApEZ/K+SQD7S5IO0ovSyUY2XVlIJxsd5RQwnoczcRNeUnsQu9IL92DIup6o6FqxECOKUvDLwPURKuqun0Q9kF7Zjo1IvrwDyKqeSX+zdIBe5xfpcLotarHlxMHmjK0/UZuW3urPxp+d8tbkbgQ5AAOkHZN6jXHw2m/PCMAEtL3TKEhtNtQhbsemotjY334vxLPg+PdQmkoooBXK1iStlc9lhKXQLByAELnxxNuvWgVZD7HTBt0pp2euFosW/OfSQ4OGDBHNncbfxXIEm1MQCYDK0J1JfCIHzIPfMwhEvpm2/uv1vkaJJoKDnN9uVJm+bcdVC0bKZdFEYBk1oDg12vgZTLrAD9u1PU42y7/xQ7mUc/MbiDz6EVXIDRHDTHfDQ1d9daMhZLCCtQdb4QOLcMM6KdgFH7X4lBNhwT/LRgsFguX1DgxcygEqd/ahhilQNQn28KuMi8YTEhgdurv9PTEsas="
        val decrypted = HappDecoder.decrypt(link)
        assertNotNull(decrypted)
        assertEquals("https://example.com/test_crypt3", decrypted)
    }

    @Test
    fun testDecryptCrypt4() {
        val link = "happ://crypt4/Cpo6I7UZJD8BdlZjOy12yb2OAsKO0ydZpGDz+exbPL9D26P+8uXvHXt4fKyLpR6pJMjGwqvjK6drHjkTweKHT46/6fx39i3osTh/iLkKemzVs8bqtaJgCMc4ECePLptirEb1NN0hrGe6fXR0ohmOzQN29f45lxxtpaRkIzxvNkgqz+jchZlTuKIKevgd3wnEZgx7d12K4yyYCDt2kFu3ECh/T1voAE6L0iSzinzcrc6BDl9OuI8XgpwO0XyosjfJJIQFHDFp2W9rd6SmRzxNC0JUYqEx4BSbqS6Y8V7+pZwR+Ctikl8zz69Fat80f8RiAuU0rX3725pk4BY0jcDfqIOiHJa340FdLHMhg9M4ZNmJAN9QVxoPlO93pXrdwDQmZ2lEJ+k+K7PU2XP6O8UuDVuZzbCsFMjW+nhxtTogjo9qO+xpVRV1rTuBoGZgxS9dUjZwvAZoqNo3nM8QfUesJAoCfISsHoWqlbBPAsYxj5daYj47kBUks6gNY1OOyhk+LsLJ64C26yoiUJz4NoBt4jKK/SY6cH6sftVE+QrGzb6OPIfgFTVm3XCNyxjIAEJ9gmF3PZEDgfwzPRASAFgufUmMu3coC7zCHs2Aiixb75022ps+Ut8fI4JDTy0sjE9A15EfVC1EiCB10DW5QCAHDOUqzOGrNzuHbf4wRA1UjBE="
        val decrypted = HappDecoder.decrypt(link)
        assertNotNull(decrypted)
        assertEquals("https://example.com/test_crypt4", decrypted)
    }

    @Test
    fun testDecryptCrypt5Legacy() {
        val link = "happ://crypt5/mtyp0nNPfx9O8LTPJbNYbaUA80CdZx:KEGoR85c8SqCxuFNJFIFa/ypMDTsNj4DmcWwECpEbJn1mj1JnpRb4+uOwSFv2QxPyXDvNxFRehTdXzmiZMF1VoCTW37I4Jj5g94SuXMjRixIeO12/fcYhdE/iBRCUJ8ppH6DyQdyM97pIjAI4ue6z9lC3jnESdSGC4p8Kz9uuUrM3RE9zd9fazMh7Xh+48z0SpOagnZRlapri3llPu7EhNQl7hm1++WmM7k48L99KkUvAMg4GmEyLlMFCSH7Z29nIT9yGZNE/fYGSBqKJjZ+9GGs04dA54V+TGSqg4xv3P3bKk9EKSIPyZw2Dbe3Yr+JdrCOPaPQ0NalYCxW/EzgQfVkuSoE9bWmy9hgu5g14gwUPFXyPf4X0/24AE0n5WDwdAcfgBMadWdOEZY4kfTw+aEAgz1a2K86Yrm8OvFPejqyBFbAA0aLpAAbU7r+/5h0fcRic6sqKR2b9pyry/f957AIMhv5sUmnuGYEpSOKhCss92cXBMFsq7yAz/ZXb3CC6cGpli2KweEGYHjUn2TP0V/WpjuK6bfOFyaqTUDFuCUkbK+zmzljAsoBdhHS/KPgG0p2o1efbnBPBksVA/Ai7tf87qfNrWV5KWh53aL6zgHDhPHHhkU70M20gzcZPq2/FBS8eCnNq118ZKFJi3GRXQ+BDlrsWMexLOKegxdj1lJnIo6J/1LA/Vx4HiiMLnvSJIgYKIDS/mSenSSv0mHLE+D++k7488G972fqSK2Wef6TJT6JcuwwQKMD3vc=ae"
        val decrypted = HappDecoder.decrypt(link)
        assertNotNull(decrypted)
        assertEquals("https://example.com/test_crypt5", decrypted)
    }

    @Test
    fun testDecryptCrypt5Salted() {
        val link = "happ://crypt5/rwlbZSVFqTOkQRjB:k881Ac/oyHM64AkfB/0m6DyGvTd9V42wDb4rRAr+uJ8zQGpizQa1vS7guPk0163C6sW1kadPPjVf08ujwxILQ/7=Jw=lylwXr1QI4lWdIWJsokhV7m4yidSEJC4BTMA3Y/C+7sP9bXySZsCWlVwM84QfUNBCObpciXHIc0+ldc7C240G92UMIwtpzk6Qz3FyVSrQbyfVPTnVAzKC9Wr+ogqGUrdPGcCFGJbhOmRAjzL2e3fuXwdUsdiLx7FnDkimNvbT2zM872Ycjj9QqRGzfuisQJG9PC1m5BQB0UFH3uoOSoaa/oZgsEjzHwujX24rOdhcscPr16n4MmREyJVcvs/wf3b86fYTrtvPvAoVPB41XztZOXCazn7y1XAhycyE4K30t8rpBpyQUyR15bVyoAidpU+D9P98g1Ruc0oP9OnbooVtyQ6SoUNrlyIdmeLfWUcKw3vBFn1g+6lPT3UhHB9jhMnPJ7WXcndOdAWwHl1BqR91685F+Ek3+VfkHHKbBfH0qmpHYeiDzQILMEAt56+KgeQYJtBBjI0R8ZZEFAf94pTN/63By97Tfv/DoruJGE4Hs9tMQMahOAxFhC782r+Qy2tD6Mi3NMGgw7OReFSoE1k3uREKkXcaQcGGv1HfuoB78gtq6b0O278o1K6q2X4iaNxxJwIc7cHU4uEwqH3hvO0LTmBN2mb3a/Rp8rPUoWKWACvLiZgzAA3AK0G8c4v6mbbytG4IqwH589RzowaBGV/pvzzdRVRsOP5UHqJ2XNl=fokdhg"
        val decrypted = HappDecoder.decrypt(link)
        assertNotNull(decrypted)
        assertEquals("https://example.com/test_crypt5old", decrypted)
    }

    @Test
    fun testDecryptV2RayTun() {
        val link = "v2raytun://crypt/RJP6vU_OGgEXxPuXb6aoavxfYvi0I0C-3bQmRznqf46KZ6jwQ6xIteUOMWyQaaghyoBwQ2hxusklMtqiOsalYIZVBUwVXTt85qSTuZtciI881Gg3Ev_YynEorsagFeWYpZBoHRUttVC2AgisX4621I2AIj5kYvHpmWtpqInpBnEK39OAYTTy3sFdXb9ch0G1Hq1LTNJt2a59UxKq3HFHvHulpebqq8XuQiKdHLGCTMpBYlQBwKDLJEwbOmSVFZiekuuG9fcuTe4a5hLDRR_eNBdKipTkK7umHFn72SfrTExBUXOCXOLFHIwt1DOIGhgKPEZ1u8GXHw2H7OfREPNCcqyaCkn0CqWiyts-dqSa6_9Z44P5la2LhJMozKCjbK827ya91IfAGlY2-7JU648qzDKiRCOIPvD1Zrfe2Y6_O7AnWnJgjmXWUFvjC-Z-9b8vIbZzb1K1MFJd57VoM8ssRGPrYbPhicHPfsGH3Kes40q5C_LkYyGqBFqvfqpB_53ewSkibCcrK4_pgx1iIA1dHlUo-8Le6N1UmnnV9PuWD0sTIbSmUo-TsHw-b6JbadDVXHznf78984lf6uduGeqYRDIbrwdnD-qOQaicnVDOblJgAhp2MFZMfv9zhwVTZ91uF_yE9cDbU93edKZRbh_noIWmgqcI16A-KF-6OCFSARI"
        val decrypted = HappDecoder.decrypt(link)
        assertNotNull(decrypted)
        assertEquals("https://example.com/test_v2", decrypted)
    }

    @Test
    fun testNestedWrappedEncryptedLink() {
        // happ://add/ wrapping an encrypted link
        val innerEncrypted = "happ://crypt/l9GixptwG4eeiwhf4yRPHO9LFmSKpYy4jmaCh5lpI8HiT7I0PY2lEN+RPz6NEZHfua842V3GNngQtjVf3R8BRaXEUTrdJOVFHyLZSkstNCL/3hmkJvMNp3Cyv2QLdndQmmNU3wYtWle5ffXcNGNrYHSOJcWTLbvRfavEJvFDHDA="
        val wrapped = "happ://add/$innerEncrypted"
        val decrypted = HappDecoder.decrypt(wrapped)
        assertEquals("https://example.com/test_crypt1", decrypted)
    }
}
