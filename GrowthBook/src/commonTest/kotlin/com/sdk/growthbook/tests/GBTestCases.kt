package com.sdk.growthbook.tests

val gbTestCases = """
    {
      "evalCondition": [
        [
          "${"$"}not     - pass",
          {
            "${"$"}not": {
              "name": "hello"
            }
          },
          {
            "name": "world"
          },
          true
        ],
        [
          "${"$"}not     - fail",
          {
            "${"$"}not": {
              "name": "hello"
            }
          },
          {
            "name": "hello"
          },
          false
        ],
        [
          "${"$"}and    /${"$"}or     - all true",
          {
            "${"$"}and": [
              {
                "father.age": {
                  "${"$"}gt": 65
                }
              },
              {
                "${"$"}or": [
                  {
                    "bday": {
                      "${"$"}regex": "-12-25${'$'}"
                    }
                  },
                  {
                    "name": "santa"
                  }
                ]
              }
            ]
          },
          {
            "name": "santa",
            "bday": "1980-12-25",
            "father": {
              "age": 70
            }
          },
          true
        ],
        [
          "${"$"}and    /${"$"}or     - first or true",
          {
            "${"$"}and": [
              {
                "father.age": {
                  "${"$"}gt": 65
                }
              },
              {
                "${"$"}or": [
                  {
                    "bday": {
                      "${"$"}regex": "-12-25${'$'}"
                    }
                  },
                  {
                    "name": "santa"
                  }
                ]
              }
            ]
          },
          {
            "name": "santa",
            "bday": "1980-12-20",
            "father": {
              "age": 70
            }
          },
          true
        ],
        [
          "${"$"}and    /${"$"}or     - second or true",
          {
            "${"$"}and": [
              {
                "father.age": {
                  "${"$"}gt": 65
                }
              },
              {
                "${"$"}or": [
                  {
                    "bday": {
                      "${"$"}regex": "-12-25${'$'}"
                    }
                  },
                  {
                    "name": "santa"
                  }
                ]
              }
            ]
          },
          {
            "name": "barbara",
            "bday": "1980-12-25",
            "father": {
              "age": 70
            }
          },
          true
        ],
        [
          "${"$"}and    /${"$"}or     - first and false",
          {
            "${"$"}and": [
              {
                "father.age": {
                  "${"$"}gt": 65
                }
              },
              {
                "${"$"}or": [
                  {
                    "bday": {
                      "${"$"}regex": "-12-25${'$'}"
                    }
                  },
                  {
                    "name": "santa"
                  }
                ]
              }
            ]
          },
          {
            "name": "santa",
            "bday": "1980-12-25",
            "father": {
              "age": 65
            }
          },
          false
        ],
        [
          "${"$"}and    /${"$"}or     - both or false",
          {
            "${"$"}and": [
              {
                "father.age": {
                  "${"$"}gt": 65
                }
              },
              {
                "${"$"}or": [
                  {
                    "bday": {
                      "${"$"}regex": "-12-25${'$'}"
                    }
                  },
                  {
                    "name": "santa"
                  }
                ]
              }
            ]
          },
          {
            "name": "barbara",
            "bday": "1980-11-25",
            "father": {
              "age": 70
            }
          },
          false
        ],
        [
          "${"$"}and    /${"$"}or     - both and false",
          {
            "${"$"}and": [
              {
                "father.age": {
                  "${"$"}gt": 65
                }
              },
              {
                "${"$"}or": [
                  {
                    "bday": {
                      "${"$"}regex": "-12-25${'$'}"
                    }
                  },
                  {
                    "name": "santa"
                  }
                ]
              }
            ]
          },
          {
            "name": "john smith",
            "bday": "1956-12-20",
            "father": {
              "age": 40
            }
          },
          false
        ],
        [
          "${"$"}exists     - false pass",
          {
            "pets.dog.name": {
              "${"$"}exists": false
            }
          },
          {
            "hello": "world"
          },
          true
        ],
        [
          "${"$"}exists     - false fail",
          {
            "pets.dog.name": {
              "${"$"}exists": false
            }
          },
          {
            "pets": {
              "dog": {
                "name": "fido"
              }
            }
          },
          false
        ],
        [
          "${"$"}exists     - true fail",
          {
            "pets.dog.name": {
              "${"$"}exists": true
            }
          },
          {
            "hello": "world"
          },
          false
        ],
        [
          "${"$"}exists     - true pass",
          {
            "pets.dog.name": {
              "${"$"}exists": true
            }
          },
          {
            "pets": {
              "dog": {
                "name": "fido"
              }
            }
          },
          true
        ],
        [
          "equals - multiple datatypes",
          {
            "str": "str",
            "num": 10,
            "flag": false
          },
          {
            "str": "str",
            "num": 10,
            "flag": false
          },
          true
        ],
        [
          "${'$'}in - pass",
          {
            "num": {
              "${'$'}in": [
                1,
                2,
                3
              ]
            }
          },
          {
            "num": 2
          },
          true
        ],
        [
          "${'$'}in - fail",
          {
            "num": {
              "${'$'}in": [
                1,
                2,
                3
              ]
            }
          },
          {
            "num": 4
          },
          false
        ],
        [
          "${"$"}nin     - pass",
          {
            "num": {
              "${"$"}nin": [
                1,
                2,
                3
              ]
            }
          },
          {
            "num": 4
          },
          true
        ],
        [
          "${"$"}nin     - fail",
          {
            "num": {
              "${"$"}nin": [
                1,
                2,
                3
              ]
            }
          },
          {
            "num": 2
          },
          false
        ],
        [
          "${"$"}elemMatch     - pass - flat arrays",
          {
            "nums": {
              "${"$"}elemMatch": {
                "${"$"}gt": 10
              }
            }
          },
          {
            "nums": [
              0,
              5,
              -20,
              15
            ]
          },
          true
        ],
        [
          "${"$"}elemMatch     - fail - flat arrays",
          {
            "nums": {
              "${"$"}elemMatch": {
                "${"$"}gt": 10
              }
            }
          },
          {
            "nums": [
              0,
              5,
              -20,
              8
            ]
          },
          false
        ],
        [
          "missing attribute - fail",
          {
            "pets.dog.name": {
              "${'$'}in": [
                "fido"
              ]
            }
          },
          {
            "hello": "world"
          },
          false
        ],
        [
          "empty ${"$"}or     - pass",
          {
            "${"$"}or": [
              
            ]
          },
          {
            "hello": "world"
          },
          true
        ],
        [
          "empty ${"$"}and     - pass",
          {
            "${"$"}and": [
              
            ]
          },
          {
            "hello": "world"
          },
          true
        ],
        [
          "empty - pass",
          {
            
          },
          {
            "hello": "world"
          },
          true
        ],
        [
          "${"$"}eq     - pass",
          {
            "occupation": {
              "${"$"}eq": "engineer"
            }
          },
          {
            "occupation": "engineer"
          },
          true
        ],
        [
          "${"$"}veq     - fail",
            {
            "version": {
               "${"$"}veq": "v1.2.3-rc.1+build123"
            }
            },
            {
               "version": "1-    2-    3-rc-    2"
            },
            false
        ],
        [
          "${"$"}vne     - pass",
            {
            "version": {
               "${"$"}vne": "v1.2.3-rc.1+build123"
            }
            },
            {
               "version": "1.3.4-rc.3"
            },
            true
        ],
        [
          "${"$"}vne     - fail",
            {
            "version": {
               "${"$"}vne": "v1.2.3-rc.1+build123"
            }
            },
            {
               "version": "1.2.3-rc.1"
            },
            false
        ],
        [
          "${"$"}vgt     - pass",
            {
            "version": {
               "${"$"}vgt": "v1.2.3-rc.1+build123"
            }
            },
            {
               "version": "1.2.4-rc.0"
            },
            true
        ],
        [
          "${"$"}vgt     - fail",
            {
            "version": {
               "${"$"}vgt": "v1.2.3-rc.1+build123"
            }
            },
            {
               "version": "1.2.2-rc.0"
            },
            false
        ],
        [
          "${"$"}vgte     - pass",
            {
            "version": {
               "${"$"}vgte": "v1.2.3-rc.1+build123"
            }
            },
            {
               "version": "1.2.3-rc.1"
            },
            true
        ],
        [
          "${"$"}vgte     - fail",
            {
            "version": {
               "${"$"}vgte": "v1.2.3-rc.1+build123"
            }
            },
            {
               "version": "1.2.2-rc.1"
            },
            false
        ],
        [
          "${"$"}vlt     - pass",
            {
            "version": {
               "${"$"}vlt": "v1.2.3-rc.1+build123"
            }
            },
            {
               "version": "1.2.2-rc.1"
            },
            true
        ],
        [
          "${"$"}vlt     - fail",
            {
            "version": {
               "${"$"}vlt": "v1.2.3-rc.1+build123"
            }
            },
            {
               "version": "1.2.4-rc.1"
            },
            false
        ],
        [
          "${"$"}vlte     - pass",
            {
            "version": {
               "${"$"}vlte": "v1.2.3-rc.1+build123"
            }
            },
            {
               "version": "1.2.3-rc.1"
            },
            true
        ],
        [
          "${"$"}vlte     - fail",
            {
            "version": {
               "${"$"}vlte": "v1.2.3-rc.1+build123"
            }
            },
            {
               "version": "1.2.4-rc.1"
            },
            false
        ],
        [
          "${"$"}eq     - fail",
          {
            "occupation": {
              "${"$"}eq": "engineer"
            }
          },
          {
            "occupation": "civil engineer"
          },
          false
        ],
        [
          "${"$"}ne     - pass",
          {
            "level": {
              "${"$"}ne": "senior"
            }
          },
          {
            "level": "junior"
          },
          true
        ],
        [
          "${"$"}ne     - fail",
          {
            "level": {
              "${"$"}ne": "senior"
            }
          },
          {
            "level": "senior"
          },
          false
        ],
        [
          "${"$"}regex     - pass",
          {
            "userAgent": {
              "${"$"}regex": "(Mobile|Tablet)"
            }
          },
          {
            "userAgent": "Android Mobile Browser"
          },
          true
        ],
        [
          "${"$"}regex     - fail",
          {
            "userAgent": {
              "${"$"}regex": "(Mobile|Tablet)"
            }
          },
          {
            "userAgent": "Chrome Desktop Browser"
          },
          false
        ],
        [
          "${"$"}gt    /${"$"}lt     numbers - pass",
          {
            "age": {
              "${"$"}gt": 30,
              "${"$"}lt": 60
            }
          },
          {
            "age": 50
          },
          true
        ],
        [
          "${"$"}gt    /${"$"}lt     numbers - fail ${"$"}lt    ",
          {
            "age": {
              "${"$"}gt": 30,
              "${"$"}lt": 60
            }
          },
          {
            "age": 60
          },
          false
        ],
        [
          "${"$"}gt    /${"$"}lt     numbers - fail ${"$"}gt    ",
          {
            "age": {
              "${"$"}gt": 30,
              "${"$"}lt": 60
            }
          },
          {
            "age": 30
          },
          false
        ],
        [
          "${"$"}gte    /${"$"}lte     numbers - pass",
          {
            "age": {
              "${"$"}gte": 30,
              "${"$"}lte": 60
            }
          },
          {
            "age": 50
          },
          true
        ],
        [
          "${"$"}gte    /${"$"}lte     numbers - pass ${"$"}gte    ",
          {
            "age": {
              "${"$"}gte": 30,
              "${"$"}lte": 60
            }
          },
          {
            "age": 30
          },
          true
        ],
        [
          "${"$"}gte    /${"$"}lte     numbers - pass ${"$"}lte    ",
          {
            "age": {
              "${"$"}gte": 30,
              "${"$"}lte": 60
            }
          },
          {
            "age": 60
          },
          true
        ],
        [
          "${"$"}gte    /${"$"}lte     numbers - fail ${"$"}lte    ",
          {
            "age": {
              "${"$"}gte": 30,
              "${"$"}lte": 60
            }
          },
          {
            "age": 61
          },
          false
        ],
        [
          "${"$"}gte    /${"$"}lte     numbers - fail ${"$"}gte    ",
          {
            "age": {
              "${"$"}gt": 30,
              "${"$"}lt": 60
            }
          },
          {
            "age": 29
          },
          false
        ],
        [
          "${"$"}gt    /${"$"}lt     strings - fail ${"$"}gt    ",
          {
            "word": {
              "${"$"}gt": "alphabet",
              "${"$"}lt": "zebra"
            }
          },
          {
            "word": "alphabet"
          },
          false
        ],
        [
          "${"$"}gt    /${"$"}lt     strings - fail ${"$"}lt    ",
          {
            "word": {
              "${"$"}gt": "alphabet",
              "${"$"}lt": "zebra"
            }
          },
          {
            "word": "zebra"
          },
          false
        ],
        [
          "${"$"}gt    /${"$"}lt     strings - pass",
          {
            "word": {
              "${"$"}gt": "alphabet",
              "${"$"}lt": "zebra"
            }
          },
          {
            "word": "always"
          },
          true
        ],
        [
          "${"$"}gt    /${"$"}lt     strings - fail uppercase",
          {
            "word": {
              "${"$"}gt": "alphabet",
              "${"$"}lt": "zebra"
            }
          },
          {
            "word": "AZL"
          },
          false
        ],
        [
          "${"$"}type     string - pass",
          {
            "a": {
              "${"$"}type": "string"
            }
          },
          {
            "a": "a"
          },
          true
        ],
        [
          "${"$"}type     string - fail",
          {
            "a": {
              "${"$"}type": "string"
            }
          },
          {
            "a": 1
          },
          false
        ],
        [
          "${"$"}type     null - pass",
          {
            "a": {
              "${"$"}type": "null"
            }
          },
          {
            "a": null
          },
          true
        ],
        [
          "${"$"}type     null - fail",
          {
            "a": {
              "${"$"}type": "null"
            }
          },
          {
            "a": 1
          },
          false
        ],
        [
          "${"$"}type     boolean - pass",
          {
            "a": {
              "${"$"}type": "boolean"
            }
          },
          {
            "a": false
          },
          true
        ],
        [
          "${"$"}type     boolean - fail",
          {
            "a": {
              "${"$"}type": "boolean"
            }
          },
          {
            "a": 1
          },
          false
        ],
        [
          "${"$"}type     number - pass",
          {
            "a": {
              "${"$"}type": "number"
            }
          },
          {
            "a": 1
          },
          true
        ],
        [
          "${"$"}type     number - fail",
          {
            "a": {
              "${"$"}type": "number"
            }
          },
          {
            "a": "a"
          },
          false
        ],
        [
          "${"$"}type     object - pass",
          {
            "a": {
              "${"$"}type": "object"
            }
          },
          {
            "a": {
              "a": "b"
            }
          },
          true
        ],
        [
          "${"$"}type     object - fail",
          {
            "a": {
              "${"$"}type": "object"
            }
          },
          {
            "a": 1
          },
          false
        ],
        [
          "${"$"}type     array - pass",
          {
            "a": {
              "${"$"}type": "array"
            }
          },
          {
            "a": [
              1,
              2
            ]
          },
          true
        ],
        [
          "${"$"}type     array - fail",
          {
            "a": {
              "${"$"}type": "array"
            }
          },
          {
            "a": 1
          },
          false
        ],
        [
          "unknown operator - pass",
          {
            "name": {
              "${"$"}regx": "hello"
            }
          },
          {
            "name": "hello"
          },
          false
        ],
        [
          "${"$"}regex     invalid - pass",
          {
            "name": {
              "${"$"}regex": "/???***[)"
            }
          },
          {
            "name": "hello"
          },
          false
        ],
        [
          "${"$"}regex     invalid - fail",
          {
            "name": {
              "${"$"}regex": "/???***[)"
            }
          },
          {
            "hello": "hello"
          },
          false
        ],
        [
          "${"$"}size     number - pass",
          {
            "tags": {
              "${"$"}size": 3
            }
          },
          {
            "tags": [
              "a",
              "b",
              "c"
            ]
          },
          true
        ],
        [
          "${"$"}size     number - fail small",
          {
            "tags": {
              "${"$"}size": 3
            }
          },
          {
            "tags": [
              "a",
              "b"
            ]
          },
          false
        ],
        [
          "${"$"}size     number - fail large",
          {
            "tags": {
              "${"$"}size": 3
            }
          },
          {
            "tags": [
              "a",
              "b",
              "c",
              "d"
            ]
          },
          false
        ],
        [
          "${"$"}size     number - fail not array",
          {
            "tags": {
              "${"$"}size": 3
            }
          },
          {
            "tags": "abc"
          },
          false
        ],
        [
          "${"$"}size     nested - pass",
          {
            "tags": {
              "${"$"}size": {
                "${"$"}gt": 2
              }
            }
          },
          {
            "tags": [
              0,
              1,
              2
            ]
          },
          true
        ],
        [
          "${"$"}size     nested - fail equal",
          {
            "tags": {
              "${"$"}size": {
                "${"$"}gt": 2
              }
            }
          },
          {
            "tags": [
              0,
              1
            ]
          },
          false
        ],
        [
          "${"$"}size     nested - fail less than",
          {
            "tags": {
              "${"$"}size": {
                "${"$"}gt": 2
              }
            }
          },
          {
            "tags": [
              0
            ]
          },
          false
        ],
        [
          "${"$"}elemMatch     nested - pass",
          {
            "hobbies": {
              "${"$"}elemMatch": {
                "name": {
                  "${"$"}regex": "^ping"
                }
              }
            }
          },
          {
            "hobbies": [
              {
                "name": "bowling"
              },
              {
                "name": "pingpong"
              },
              {
                "name": "tennis"
              }
            ]
          },
          true
        ],
        [
          "${"$"}elemMatch     nested - fail",
          {
            "hobbies": {
              "${"$"}elemMatch": {
                "name": {
                  "${"$"}regex": "^ping"
                }
              }
            }
          },
          {
            "hobbies": [
              {
                "name": "bowling"
              },
              {
                "name": "tennis"
              }
            ]
          },
          false
        ],
        [
          "${"$"}elemMatch     nested - fail not array",
          {
            "hobbies": {
              "${"$"}elemMatch": {
                "name": {
                  "${"$"}regex": "^ping"
                }
              }
            }
          },
          {
            "hobbies": "all"
          },
          false
        ],
        [
          "${"$"}not     - pass",
          {
            "name": {
              "${"$"}not": {
                "${"$"}regex": "^hello"
              }
            }
          },
          {
            "name": "world"
          },
          true
        ],
        [
          "${"$"}not     - fail",
          {
            "name": {
              "${"$"}not": {
                "${"$"}regex": "^hello"
              }
            }
          },
          {
            "name": "hello world"
          },
          false
        ],
        [
          "${"$"}all     - pass",
          {
            "tags": {
              "${"$"}all": [
                "one",
                "three"
              ]
            }
          },
          {
            "tags": [
              "one",
              "two",
              "three"
            ]
          },
          true
        ],
        [
          "${"$"}all     - fail",
          {
            "tags": {
              "${"$"}all": [
                "one",
                "three"
              ]
            }
          },
          {
            "tags": [
              "one",
              "two",
              "four"
            ]
          },
          false
        ],
        [
          "${"$"}all     - fail not array",
          {
            "tags": {
              "${"$"}all": [
                "one",
                "three"
              ]
            }
          },
          {
            "tags": "hello"
          },
          false
        ],
        [
          "${"$"}nor     - pass",
          {
            "${"$"}nor": [
              {
                "name": "john"
              },
              {
                "age": {
                  "${"$"}lt": 30
                }
              }
            ]
          },
          {
            "name": "jim",
            "age": 40
          },
          true
        ],
        [
          "${"$"}nor     - fail both",
          {
            "${"$"}nor": [
              {
                "name": "john"
              },
              {
                "age": {
                  "${"$"}lt": 30
                }
              }
            ]
          },
          {
            "name": "john",
            "age": 20
          },
          false
        ],
        [
          "${"$"}nor     - fail first",
          {
            "${"$"}nor": [
              {
                "name": "john"
              },
              {
                "age": {
                  "${"$"}lt": 30
                }
              }
            ]
          },
          {
            "name": "john",
            "age": 40
          },
          false
        ],
        [
          "${"$"}nor     - fail second",
          {
            "${"$"}nor": [
              {
                "name": "john"
              },
              {
                "age": {
                  "${"$"}lt": 30
                }
              }
            ]
          },
          {
            "name": "jim",
            "age": 20
          },
          false
        ],
        [
          "equals array - pass",
          {
            "tags": [
              "hello",
              "world"
            ]
          },
          {
            "tags": [
              "hello",
              "world"
            ]
          },
          true
        ],
        [
          "equals array - fail order",
          {
            "tags": [
              "hello",
              "world"
            ]
          },
          {
            "tags": [
              "world",
              "hello"
            ]
          },
          false
        ],
        [
          "equals array - fail missing item",
          {
            "tags": [
              "hello",
              "world"
            ]
          },
          {
            "tags": [
              "hello"
            ]
          },
          false
        ],
        [
          "equals array - fail extra item",
          {
            "tags": [
              "hello",
              "world"
            ]
          },
          {
            "tags": [
              "hello",
              "world",
              "foo"
            ]
          },
          false
        ],
        [
          "equals array - fail type mismatch",
          {
            "tags": [
              "hello",
              "world"
            ]
          },
          {
            "tags": "hello world"
          },
          false
        ],
        [
          "equals object - pass",
          {
            "tags": {
              "hello": "world"
            }
          },
          {
            "tags": {
              "hello": "world"
            }
          },
          true
        ],
        [
          "equals object - fail extra property",
          {
            "tags": {
              "hello": "world"
            }
          },
          {
            "tags": {
              "hello": "world",
              "yes": "please"
            }
          },
          false
        ],
        [
          "equals object - fail missing property",
          {
            "tags": {
              "hello": "world"
            }
          },
          {
            "tags": {
              
            }
          },
          false
        ],
        [
          "equals object - fail type mismatch",
          {
            "tags": {
              "hello": "world"
            }
          },
          {
            "tags": "hello world"
          },
          false
        ]
      ],
      "hash": [
        [
          "a",
          0.22,
          1,
          ""
        ],
        [
          "b",
          0.077,
          1,
          ""
        ],
        [
          "ab",
          0.946,
          1,
          ""
        ],
        [
          "def",
          0.652,
          1,
          ""
        ],
        [
          "8952klfjas09ujkasdf",
          0.549,
          1,
          ""
        ],
        [
          "123",
          0.011,
          1,
          ""
        ],
        [
          "___)((*\":&",
          0.563,
          1,
          ""
        ],
        [
          "a",
          0.0505,
          2,
          "seed"
        ]
      ],
      "getBucketRange": [
        [
          "normal 50/50",
          [
            2,
            1,
            null
          ],
          [
            [
              0,
              0.5
            ],
            [
              0.5,
              1
            ]
          ]
        ],
        [
          "reduced coverage",
          [
            2,
            0.5,
            null
          ],
          [
            [
              0,
              0.25
            ],
            [
              0.5,
              0.75
            ]
          ]
        ],
        [
          "zero coverage",
          [
            2,
            0,
            null
          ],
          [
            [
              0,
              0
            ],
            [
              0.5,
              0.5
            ]
          ]
        ],
        [
          "4 variations",
          [
            4,
            1,
            null
          ],
          [
            [
              0,
              0.25
            ],
            [
              0.25,
              0.5
            ],
            [
              0.5,
              0.75
            ],
            [
              0.75,
              1
            ]
          ]
        ],
        [
          "uneven weights",
          [
            2,
            1,
            [
              0.4,
              0.6
            ]
          ],
          [
            [
              0,
              0.4
            ],
            [
              0.4,
              1
            ]
          ]
        ],
        [
          "uneven weights, 3 variations",
          [
            3,
            1,
            [
              0.2,
              0.3,
              0.5
            ]
          ],
          [
            [
              0,
              0.2
            ],
            [
              0.2,
              0.5
            ],
            [
              0.5,
              1
            ]
          ]
        ],
        [
          "uneven weights, reduced coverage, 3 variations",
          [
            3,
            0.2,
            [
              0.2,
              0.3,
              0.5
            ]
          ],
          [
            [
              0,
              0.04
            ],
            [
              0.2,
              0.26
            ],
            [
              0.5,
              0.6
            ]
          ]
        ],
        [
          "negative coverage",
          [
            2,
            -0.2,
            null
          ],
          [
            [
              0,
              0
            ],
            [
              0.5,
              0.5
            ]
          ]
        ],
        [
          "coverage above 1",
          [
            2,
            1.5,
            null
          ],
          [
            [
              0,
              0.5
            ],
            [
              0.5,
              1
            ]
          ]
        ],
        [
          "weights sum below 1",
          [
            2,
            1,
            [
              0.4,
              0.1
            ]
          ],
          [
            [
              0,
              0.5
            ],
            [
              0.5,
              1
            ]
          ]
        ],
        [
          "weights sum above 1",
          [
            2,
            1,
            [
              0.7,
              0.6
            ]
          ],
          [
            [
              0,
              0.5
            ],
            [
              0.5,
              1
            ]
          ]
        ],
        [
          "weights.length not equal to num variations",
          [
            4,
            1,
            [
              0.4,
              0.4,
              0.2
            ]
          ],
          [
            [
              0,
              0.25
            ],
            [
              0.25,
              0.5
            ],
            [
              0.5,
              0.75
            ],
            [
              0.75,
              1
            ]
          ]
        ],
        [
          "weights sum almost equals 1",
          [
            2,
            1,
            [
              0.4,
              0.5999
            ]
          ],
          [
            [
              0,
              0.4
            ],
            [
              0.4,
              0.9999
            ]
          ]
        ]
      ],
      "feature": [
        [
          "unknown feature key",
          {
            
          },
          "my-feature",
          {
            "value": null,
            "on": false,
            "off": true,
            "source": "unknownFeature"
          }
        ],
        [
          "defaults when empty",
          {
            "features": {
              "feature": {
                
              }
            }
          },
          "feature",
          {
            "value": null,
            "on": false,
            "off": true,
            "source": "defaultValue"
          }
        ],
        [
          "uses defaultValue - number",
          {
            "features": {
              "feature": {
                "defaultValue": 1
              }
            }
          },
          "feature",
          {
            "value": 1,
            "on": true,
            "off": false,
            "source": "defaultValue"
          }
        ],
        [
          "uses custom values - string",
          {
            "features": {
              "feature": {
                "defaultValue": "yes"
              }
            }
          },
          "feature",
          {
            "value": "yes",
            "on": true,
            "off": false,
            "source": "defaultValue"
          }
        ],
        [
          "force rules",
          {
            "features": {
              "feature": {
                "defaultValue": 2,
                "rules": [
                  {
                    "force": 1
                  }
                ]
              }
            }
          },
          "feature",
          {
            "value": 1,
            "on": true,
            "off": false,
            "source": "force"
          }
        ],
        [
          "force rules - coverage included",
          {
            "attributes": {
              "id": "3"
            },
            "features": {
              "feature": {
                "defaultValue": 2,
                "rules": [
                  {
                    "force": 1,
                    "coverage": 0.5
                  }
                ]
              }
            }
          },
          "feature",
          {
            "value": 1,
            "on": true,
            "off": false,
            "source": "force"
          }
        ],
        [
          "force rules - coverage excluded",
          {
            "attributes": {
              "id": "1"
            },
            "features": {
              "feature": {
                "defaultValue": 2,
                "rules": [
                  {
                    "force": 1,
                    "coverage": 0.5
                  }
                ]
              }
            }
          },
          "feature",
          {
            "value": 2,
            "on": true,
            "off": false,
            "source": "defaultValue"
          }
        ],
        [
          "force rules - coverage missing hashAttribute",
          {
            "attributes": {
              
            },
            "features": {
              "feature": {
                "defaultValue": 2,
                "rules": [
                  {
                    "force": 1,
                    "coverage": 0.5
                  }
                ]
              }
            }
          },
          "feature",
          {
            "value": 2,
            "on": true,
            "off": false,
            "source": "defaultValue"
          }
        ],
        [
          "force rules - condition pass",
          {
            "attributes": {
              "country": "US",
              "browser": "firefox"
            },
            "features": {
              "feature": {
                "defaultValue": 2,
                "rules": [
                  {
                    "force": 1,
                    "condition": {
                      "country": {
                        "${'$'}in": [
                          "US",
                          "CA"
                        ]
                      },
                      "browser": "firefox"
                    }
                  }
                ]
              }
            }
          },
          "feature",
          {
            "value": 1,
            "on": true,
            "off": false,
            "source": "force"
          }
        ],
        [
          "force rules - condition fail",
          {
            "attributes": {
              "country": "US",
              "browser": "chrome"
            },
            "features": {
              "feature": {
                "defaultValue": 2,
                "rules": [
                  {
                    "force": 1,
                    "condition": {
                      "country": {
                        "${'$'}in": [
                          "US",
                          "CA"
                        ]
                      },
                      "browser": "firefox"
                    }
                  }
                ]
              }
            }
          },
          "feature",
          {
            "value": 2,
            "on": true,
            "off": false,
            "source": "defaultValue"
          }
        ],
        [
          "ignores empty rules",
          {
            "features": {
              "feature": {
                "rules": [
                  {
                    
                  }
                ]
              }
            }
          },
          "feature",
          {
            "value": null,
            "on": false,
            "off": true,
            "source": "defaultValue"
          }
        ],
        [
          "empty experiment rule - c",
          {
            "attributes": {
              "id": "123"
            },
            "features": {
              "feature": {
                "rules": [
                  {
                    "variations": [
                      "a",
                      "b",
                      "c"
                    ]
                  }
                ]
              }
            }
          },
          "feature",
          {
            "value": "c",
            "on": true,
            "off": false,
            "experiment": {
              "key": "feature",
              "variations": [
                "a",
                "b",
                "c"
              ]
            },
            "experimentResult": {
              "value": "c",
              "variationId": 2,
              "inExperiment": true,
              "hashAttribute": "id",
              "hashValue": "123"
            },
            "source": "experiment"
          }
        ],
        [
          "empty experiment rule - a",
          {
            "attributes": {
              "id": "456"
            },
            "features": {
              "feature": {
                "rules": [
                  {
                    "variations": [
                      "a",
                      "b",
                      "c"
                    ]
                  }
                ]
              }
            }
          },
          "feature",
          {
            "value": "a",
            "on": true,
            "off": false,
            "experiment": {
              "key": "feature",
              "variations": [
                "a",
                "b",
                "c"
              ]
            },
            "experimentResult": {
              "value": "a",
              "variationId": 0,
              "inExperiment": true,
              "hashAttribute": "id",
              "hashValue": "456"
            },
            "source": "experiment"
          }
        ],
        [
          "empty experiment rule - b",
          {
            "attributes": {
              "id": "fds"
            },
            "features": {
              "feature": {
                "rules": [
                  {
                    "variations": [
                      "a",
                      "b",
                      "c"
                    ]
                  }
                ]
              }
            }
          },
          "feature",
          {
            "value": "b",
            "on": true,
            "off": false,
            "experiment": {
              "key": "feature",
              "variations": [
                "a",
                "b",
                "c"
              ]
            },
            "experimentResult": {
              "value": "b",
              "variationId": 1,
              "inExperiment": true,
              "hashAttribute": "id",
              "hashValue": "fds"
            },
            "source": "experiment"
          }
        ],
        [
          "creates experiments properly",
          {
            "attributes": {
              "anonId": "123",
              "premium": true
            },
            "features": {
              "feature": {
                "rules": [
                  {
                    "coverage": 0.99,
                    "hashAttribute": "anonId",
                    "namespace": [
                      "pricing",
                      0,
                      1
                    ],
                    "key": "hello",
                    "variations": [
                      true,
                      false
                    ],
                    "weights": [
                      0.1,
                      0.9
                    ],
                    "condition": {
                      "premium": true
                    }
                  }
                ]
              }
            }
          },
          "feature",
          {
            "value": false,
            "on": false,
            "off": true,
            "source": "experiment",
            "experiment": {
              "coverage": 0.99,
              "hashAttribute": "anonId",
              "namespace": [
                "pricing",
                0,
                1
              ],
              "key": "hello",
              "variations": [
                true,
                false
              ],
              "weights": [
                0.1,
                0.9
              ]
            },
            "experimentResult": {
              "value": false,
              "variationId": 1,
              "inExperiment": true,
              "hashAttribute": "anonId",
              "hashValue": "123"
            }
          }
        ],
        [
          "rule orders - skip 1",
          {
            "attributes": {
              "browser": "firefox"
            },
            "features": {
              "feature": {
                "defaultValue": 0,
                "rules": [
                  {
                    "force": 1,
                    "condition": {
                      "browser": "chrome"
                    }
                  },
                  {
                    "force": 2,
                    "condition": {
                      "browser": "firefox"
                    }
                  },
                  {
                    "force": 3,
                    "condition": {
                      "browser": "safari"
                    }
                  }
                ]
              }
            }
          },
          "feature",
          {
            "value": 2,
            "on": true,
            "off": false,
            "source": "force"
          }
        ],
        [
          "rule orders - skip 1,2",
          {
            "attributes": {
              "browser": "safari"
            },
            "features": {
              "feature": {
                "defaultValue": 0,
                "rules": [
                  {
                    "force": 1,
                    "condition": {
                      "browser": "chrome"
                    }
                  },
                  {
                    "force": 2,
                    "condition": {
                      "browser": "firefox"
                    }
                  },
                  {
                    "force": 3,
                    "condition": {
                      "browser": "safari"
                    }
                  }
                ]
              }
            }
          },
          "feature",
          {
            "value": 3,
            "on": true,
            "off": false,
            "source": "force"
          }
        ],
        [
          "rule orders - skip all",
          {
            "attributes": {
              "browser": "ie"
            },
            "features": {
              "feature": {
                "defaultValue": 0,
                "rules": [
                  {
                    "force": 1,
                    "condition": {
                      "browser": "chrome"
                    }
                  },
                  {
                    "force": 2,
                    "condition": {
                      "browser": "firefox"
                    }
                  },
                  {
                    "force": 3,
                    "condition": {
                      "browser": "safari"
                    }
                  }
                ]
              }
            }
          },
          "feature",
          {
            "value": 0,
            "on": false,
            "off": true,
            "source": "defaultValue"
          }
        ],
        [
          "skips experiment on coverage",
          {
            "attributes": {
              "id": "123"
            },
            "features": {
              "feature": {
                "defaultValue": 0,
                "rules": [
                  {
                    "variations": [
                      0,
                      1,
                      2,
                      3
                    ],
                    "coverage": 0.01
                  },
                  {
                    "force": 3
                  }
                ]
              }
            }
          },
          "feature",
          {
            "value": 3,
            "on": true,
            "off": false,
            "source": "force"
          }
        ],
        [
          "skips experiment on namespace",
          {
            "attributes": {
              "id": "123"
            },
            "features": {
              "feature": {
                "defaultValue": 0,
                "rules": [
                  {
                    "variations": [
                      0,
                      1,
                      2,
                      3
                    ],
                    "namespace": [
                      "pricing",
                      0,
                      0.01
                    ]
                  },
                  {
                    "force": 3
                  }
                ]
              }
            }
          },
          "feature",
          {
            "value": 3,
            "on": true,
            "off": false,
            "source": "force"
          }
        ],
        [
          "skip experiment on missing hashAttribute",
          {
            "attributes": {
              "id": "123"
            },
            "features": {
              "feature": {
                "defaultValue": 0,
                "rules": [
                  {
                    "variations": [
                      0,
                      1,
                      2,
                      3
                    ],
                    "hashAttribute": "company"
                  },
                  {
                    "force": 3
                  }
                ]
              }
            }
          },
          "feature",
          {
            "value": 3,
            "on": true,
            "off": false,
            "source": "force"
          }
        ]
      ],
      "run": [
        [
          "default weights - 1",
          {
            "attributes": {
              "id": "1"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ]
          },
          1,
          true
        ],
        [
          "default weights - 2",
          {
            "attributes": {
              "id": "2"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ]
          },
          0,
          true
        ],
        [
          "default weights - 3",
          {
            "attributes": {
              "id": "3"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ]
          },
          0,
          true
        ],
        [
          "default weights - 4",
          {
            "attributes": {
              "id": "4"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ]
          },
          1,
          true
        ],
        [
          "default weights - 5",
          {
            "attributes": {
              "id": "5"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ]
          },
          1,
          true
        ],
        [
          "default weights - 6",
          {
            "attributes": {
              "id": "6"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ]
          },
          1,
          true
        ],
        [
          "default weights - 7",
          {
            "attributes": {
              "id": "7"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ]
          },
          0,
          true
        ],
        [
          "default weights - 8",
          {
            "attributes": {
              "id": "8"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ]
          },
          1,
          true
        ],
        [
          "default weights - 9",
          {
            "attributes": {
              "id": "9"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ]
          },
          0,
          true
        ],
        [
          "uneven weights - 1",
          {
            "attributes": {
              "id": "1"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ],
            "weights": [
              0.1,
              0.9
            ]
          },
          1,
          true
        ],
        [
          "uneven weights - 2",
          {
            "attributes": {
              "id": "2"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ],
            "weights": [
              0.1,
              0.9
            ]
          },
          1,
          true
        ],
        [
          "uneven weights - 3",
          {
            "attributes": {
              "id": "3"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ],
            "weights": [
              0.1,
              0.9
            ]
          },
          0,
          true
        ],
        [
          "uneven weights - 4",
          {
            "attributes": {
              "id": "4"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ],
            "weights": [
              0.1,
              0.9
            ]
          },
          1,
          true
        ],
        [
          "uneven weights - 5",
          {
            "attributes": {
              "id": "5"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ],
            "weights": [
              0.1,
              0.9
            ]
          },
          1,
          true
        ],
        [
          "uneven weights - 6",
          {
            "attributes": {
              "id": "6"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ],
            "weights": [
              0.1,
              0.9
            ]
          },
          1,
          true
        ],
        [
          "uneven weights - 7",
          {
            "attributes": {
              "id": "7"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ],
            "weights": [
              0.1,
              0.9
            ]
          },
          0,
          true
        ],
        [
          "uneven weights - 8",
          {
            "attributes": {
              "id": "8"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ],
            "weights": [
              0.1,
              0.9
            ]
          },
          1,
          true
        ],
        [
          "uneven weights - 9",
          {
            "attributes": {
              "id": "9"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ],
            "weights": [
              0.1,
              0.9
            ]
          },
          1,
          true
        ],
        [
          "coverage - 1",
          {
            "attributes": {
              "id": "1"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ],
            "coverage": 0.4
          },
          0,
          false
        ],
        [
          "coverage - 2",
          {
            "attributes": {
              "id": "2"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ],
            "coverage": 0.4
          },
          0,
          true
        ],
        [
          "coverage - 3",
          {
            "attributes": {
              "id": "3"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ],
            "coverage": 0.4
          },
          0,
          true
        ],
        [
          "coverage - 4",
          {
            "attributes": {
              "id": "4"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ],
            "coverage": 0.4
          },
          0,
          false
        ],
        [
          "coverage - 5",
          {
            "attributes": {
              "id": "5"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ],
            "coverage": 0.4
          },
          1,
          true
        ],
        [
          "coverage - 6",
          {
            "attributes": {
              "id": "6"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ],
            "coverage": 0.4
          },
          0,
          false
        ],
        [
          "coverage - 7",
          {
            "attributes": {
              "id": "7"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ],
            "coverage": 0.4
          },
          0,
          true
        ],
        [
          "coverage - 8",
          {
            "attributes": {
              "id": "8"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ],
            "coverage": 0.4
          },
          1,
          true
        ],
        [
          "coverage - 9",
          {
            "attributes": {
              "id": "9"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ],
            "coverage": 0.4
          },
          0,
          false
        ],
        [
          "three way test - 1",
          {
            "attributes": {
              "id": "1"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1,
              2
            ]
          },
          2,
          true
        ],
        [
          "three way test - 2",
          {
            "attributes": {
              "id": "2"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1,
              2
            ]
          },
          0,
          true
        ],
        [
          "three way test - 3",
          {
            "attributes": {
              "id": "3"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1,
              2
            ]
          },
          0,
          true
        ],
        [
          "three way test - 4",
          {
            "attributes": {
              "id": "4"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1,
              2
            ]
          },
          2,
          true
        ],
        [
          "three way test - 5",
          {
            "attributes": {
              "id": "5"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1,
              2
            ]
          },
          1,
          true
        ],
        [
          "three way test - 6",
          {
            "attributes": {
              "id": "6"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1,
              2
            ]
          },
          2,
          true
        ],
        [
          "three way test - 7",
          {
            "attributes": {
              "id": "7"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1,
              2
            ]
          },
          0,
          true
        ],
        [
          "three way test - 8",
          {
            "attributes": {
              "id": "8"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1,
              2
            ]
          },
          1,
          true
        ],
        [
          "three way test - 9",
          {
            "attributes": {
              "id": "9"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1,
              2
            ]
          },
          0,
          true
        ],
        [
          "test name - my-test",
          {
            "attributes": {
              "id": "1"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ]
          },
          1,
          true
        ],
        [
          "test name - my-test-3",
          {
            "attributes": {
              "id": "1"
            }
          },
          {
            "key": "my-test-3",
            "variations": [
              0,
              1
            ]
          },
          0,
          true
        ],
        [
          "empty id",
          {
            "attributes": {
              "id": ""
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ]
          },
          0,
          false
        ],
        [
          "missing id",
          {
            "attributes": {
              
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ]
          },
          0,
          false
        ],
        [
          "missing attributes",
          {
            
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ]
          },
          0,
          false
        ],
        [
          "single variation",
          {
            "attributes": {
              "id": "1"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0
            ]
          },
          0,
          false
        ],
        [
          "negative forced variation",
          {
            "attributes": {
              "id": "1"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ],
            "force": -8
          },
          0,
          false
        ],
        [
          "high forced variation",
          {
            "attributes": {
              "id": "1"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ],
            "force": 25
          },
          0,
          false
        ],
        [
          "evaluates conditions - pass",
          {
            "attributes": {
              "id": "1",
              "browser": "firefox"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ],
            "condition": {
              "browser": "firefox"
            }
          },
          1,
          true
        ],
        [
          "evaluates conditions - fail",
          {
            "attributes": {
              "id": "1",
              "browser": "chrome"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ],
            "condition": {
              "browser": "firefox"
            }
          },
          0,
          false
        ],
        [
          "custom hashAttribute",
          {
            "attributes": {
              "id": "2",
              "companyId": "1"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ],
            "hashAttribute": "companyId"
          },
          1,
          true
        ],
        [
          "globally disabled",
          {
            "attributes": {
              "id": "1"
            },
            "enabled": false
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ]
          },
          0,
          false
        ],
        [
          "run active experiments",
          {
            "attributes": {
              "id": "1"
            }
          },
          {
            "key": "my-test",
            "active": true,
            "variations": [
              0,
              1
            ]
          },
          1,
          true
        ],
        [
          "skip inactive experiments",
          {
            "attributes": {
              "id": "1"
            }
          },
          {
            "key": "my-test",
            "active": false,
            "variations": [
              0,
              1
            ]
          },
          0,
          false
        ],
        [
          "coverage take precendence over forced",
          {
            "attributes": {
              "id": "1"
            }
          },
          {
            "key": "my-test",
            "force": 1,
            "coverage": 0.01,
            "variations": [
              0,
              1
            ]
          },
          0,
          false
        ],
        [
          "JSON values for experiments",
          {
            "attributes": {
              "id": "1"
            }
          },
          {
            "key": "my-test",
            "variations": [
              {
                "color": "blue",
                "size": "small"
              },
              {
                "color": "green",
                "size": "large"
              }
            ]
          },
          {
            "color": "green",
            "size": "large"
          },
          true
        ],
        [
    "Force variation from context",
    {
      "attributes": {
        "id": "1"
      },
      "forcedVariations": {
        "my-test": 0
      }
    },
    {
      "key": "my-test",
      "variations": [
        0,
        1
      ]
    },
    0,
    false
  ],
        [
          "Skips experiments in QA mode",
          {
            "attributes": {
              "id": "1"
            },
            "qaMode": true
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ]
          },
          0,
          false
        ],
        [
          "Works in QA mode if forced in context",
          {
            "attributes": {
              "id": "1"
            },
            "qaMode": true,
            "forcedVariations": {
              "my-test": 1
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ]
          },
          1,
          false
        ],
        [
    "Works in QA mode if forced in experiment",
    {
      "attributes": {
        "id": "1"
      },
      "qaMode": true
    },
    {
      "key": "my-test",
      "variations": [
        0,
        1
      ],
      "force": 1
    },
    1,
    false
  ],
        [
          "Experiment namespace - pass",
          {
            "attributes": {
              "id": "1"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ],
            "namespace": [
              "namespace",
              0.1,
              1
            ]
          },
          1,
          true
        ],
        [
          "Experiment namespace - fail",
          {
            "attributes": {
              "id": "1"
            }
          },
          {
            "key": "my-test",
            "variations": [
              0,
              1
            ],
            "namespace": [
              "namespace",
              0,
              0.1
            ]
          },
          0,
          false
        ]
      ],
      "chooseVariation": [
        [
          "even range, 0.2",
          0.2,
          [
            [
              0,
              0.5
            ],
            [
              0.5,
              1
            ]
          ],
          0
        ],
        [
          "even range, 0.4",
          0.4,
          [
            [
              0,
              0.5
            ],
            [
              0.5,
              1
            ]
          ],
          0
        ],
        [
          "even range, 0.6",
          0.6,
          [
            [
              0,
              0.5
            ],
            [
              0.5,
              1
            ]
          ],
          1
        ],
        [
          "even range, 0.8",
          0.8,
          [
            [
              0,
              0.5
            ],
            [
              0.5,
              1
            ]
          ],
          1
        ],
        [
          "even range, 0",
          0,
          [
            [
              0,
              0.5
            ],
            [
              0.5,
              1
            ]
          ],
          0
        ],
        [
          "even range, 0.5",
          0.5,
          [
            [
              0,
              0.5
            ],
            [
              0.5,
              1
            ]
          ],
          1
        ],
        [
          "reduced range, 0.2",
          0.2,
          [
            [
              0,
              0.25
            ],
            [
              0.5,
              0.75
            ]
          ],
          0
        ],
        [
          "reduced range, 0.4",
          0.4,
          [
            [
              0,
              0.25
            ],
            [
              0.5,
              0.75
            ]
          ],
          -1
        ],
        [
          "reduced range, 0.6",
          0.6,
          [
            [
              0,
              0.25
            ],
            [
              0.5,
              0.75
            ]
          ],
          1
        ],
        [
          "reduced range, 0.8",
          0.8,
          [
            [
              0,
              0.25
            ],
            [
              0.5,
              0.75
            ]
          ],
          -1
        ],
        [
          "reduced range, 0.25",
          0.25,
          [
            [
              0,
              0.25
            ],
            [
              0.5,
              0.75
            ]
          ],
          -1
        ],
        [
          "reduced range, 0.5",
          0.5,
          [
            [
              0,
              0.25
            ],
            [
              0.5,
              0.75
            ]
          ],
          1
        ],
        [
          "zero range",
          0.5,
          [
            [
              0,
              0.5
            ],
            [
              0.5,
              0.5
            ],
            [
              0.5,
              1
            ]
          ],
          2
        ]
      ],
      "inNamespace": [
        [
          "user 1, namespace1, 1",
          "1",
          [
            "namespace1",
            0,
            0.4
          ],
          false
        ],
        [
          "user 1, namespace1, 2",
          "1",
          [
            "namespace1",
            0.4,
            1
          ],
          true
        ],
        [
          "user 1, namespace2, 1",
          "1",
          [
            "namespace2",
            0,
            0.4
          ],
          false
        ],
        [
          "user 1, namespace2, 2",
          "1",
          [
            "namespace2",
            0.4,
            1
          ],
          true
        ],
        [
          "user 2, namespace1, 1",
          "2",
          [
            "namespace1",
            0,
            0.4
          ],
          false
        ],
        [
          "user 2, namespace1, 2",
          "2",
          [
            "namespace1",
            0.4,
            1
          ],
          true
        ],
        [
          "user 2, namespace2, 1",
          "2",
          [
            "namespace2",
            0,
            0.4
          ],
          false
        ],
        [
          "user 2, namespace2, 2",
          "2",
          [
            "namespace2",
            0.4,
            1
          ],
          true
        ],
        [
          "user 3, namespace1, 1",
          "3",
          [
            "namespace1",
            0,
            0.4
          ],
          false
        ],
        [
          "user 3, namespace1, 2",
          "3",
          [
            "namespace1",
            0.4,
            1
          ],
          true
        ],
        [
          "user 3, namespace2, 1",
          "3",
          [
            "namespace2",
            0,
            0.4
          ],
          true
        ],
        [
          "user 3, namespace2, 2",
          "3",
          [
            "namespace2",
            0.4,
            1
          ],
          false
        ],
        [
          "user 4, namespace1, 1",
          "4",
          [
            "namespace1",
            0,
            0.4
          ],
          false
        ],
        [
          "user 4, namespace1, 2",
          "4",
          [
            "namespace1",
            0.4,
            1
          ],
          true
        ],
        [
          "user 4, namespace2, 1",
          "4",
          [
            "namespace2",
            0,
            0.4
          ],
          true
        ],
        [
          "user 4, namespace2, 2",
          "4",
          [
            "namespace2",
            0.4,
            1
          ],
          false
        ]
      ],
      "getEqualWeights": [
        [
          -1,
          [
            
          ]
        ],
        [
          0,
          [
            
          ]
        ],
        [
          1,
          [
            1
          ]
        ],
        [
          2,
          [
            0.5,
            0.5
          ]
        ],
        [
          3,
          [
            0.33333333,
            0.33333333,
            0.33333333
          ]
        ],
        [
          4,
          [
            0.25,
            0.25,
            0.25,
            0.25
          ]
        ]
      ]
    }
""".trimIndent()
/*val gbTestCases = """
    {
      "specVersion": "0.5.0",
      "evalFeature": [
        {
          "name": "unknown feature key",
          "type": "null",
          "context": {
            "features": "{}",
            "attributes": "null",
            "forcedVariations": null
          },
          "feature": "my-feature",
          "result": "{\"on\":false,\"off\":true,\"source\":\"unknownFeature\",\"value\":null,\"experiment\":null,\"experimentResult\":null}"
        },
        {
          "name": "defaults when empty",
          "type": "null",
          "context": {
            "features": "{\"feature\":{}}",
            "attributes": "null",
            "forcedVariations": null
          },
          "feature": "feature",
          "result": "{\"on\":false,\"off\":true,\"source\":\"defaultValue\",\"value\":null,\"experiment\":null,\"experimentResult\":null}"
        },
        {
          "name": "uses defaultValue - number",
          "type": "integer",
          "context": {
            "features": "{\"feature\":{\"defaultValue\":1}}",
            "attributes": "null",
            "forcedVariations": null
          },
          "feature": "feature",
          "result": "{\"on\":true,\"off\":false,\"source\":\"defaultValue\",\"value\":1,\"experiment\":null,\"experimentResult\":null}"
        },
        {
          "name": "uses custom values - string",
          "type": "string",
          "context": {
            "features": "{\"feature\":{\"defaultValue\":\"yes\"}}",
            "attributes": "null",
            "forcedVariations": null
          },
          "feature": "feature",
          "result": "{\"on\":true,\"off\":false,\"source\":\"defaultValue\",\"value\":\"yes\",\"experiment\":null,\"experimentResult\":null}"
        },
        {
          "name": "force rules",
          "type": "integer",
          "context": {
            "features": "{\"feature\":{\"defaultValue\":2,\"rules\":[{\"force\":1}]}}",
            "attributes": "null",
            "forcedVariations": null
          },
          "feature": "feature",
          "result": "{\"on\":true,\"off\":false,\"source\":\"force\",\"value\":1,\"experiment\":null,\"experimentResult\":null}"
        },
        {
          "name": "force rules - force false",
          "type": "boolean",
          "context": {
            "features": "{\"feature\":{\"defaultValue\":true,\"rules\":[{\"force\":false}]}}",
            "attributes": "null",
            "forcedVariations": null
          },
          "feature": "feature",
          "result": "{\"on\":false,\"off\":true,\"source\":\"force\",\"value\":false,\"experiment\":null,\"experimentResult\":null}"
        },
        {
          "name": "force rules - coverage included",
          "type": "integer",
          "context": {
            "features": "{\"feature\":{\"defaultValue\":2,\"rules\":[{\"force\":1,\"coverage\":0.5}]}}",
            "attributes": "{\"id\":\"3\"}",
            "forcedVariations": null
          },
          "feature": "feature",
          "result": "{\"on\":true,\"off\":false,\"source\":\"force\",\"value\":1,\"experiment\":null,\"experimentResult\":null}"
        },
        {
          "name": "force rules - coverage excluded",
          "type": "integer",
          "context": {
            "features": "{\"feature\":{\"defaultValue\":2,\"rules\":[{\"force\":1,\"coverage\":0.5}]}}",
            "attributes": "{\"id\":\"1\"}",
            "forcedVariations": null
          },
          "feature": "feature",
          "result": "{\"on\":true,\"off\":false,\"source\":\"defaultValue\",\"value\":2,\"experiment\":null,\"experimentResult\":null}"
        },
        {
          "name": "force rules - coverage missing hashAttribute",
          "type": "integer",
          "context": {
            "features": "{\"feature\":{\"defaultValue\":2,\"rules\":[{\"force\":1,\"coverage\":0.5}]}}",
            "attributes": "{}",
            "forcedVariations": null
          },
          "feature": "feature",
          "result": "{\"on\":true,\"off\":false,\"source\":\"defaultValue\",\"value\":2,\"experiment\":null,\"experimentResult\":null}"
        },
        {
          "name": "force rules - condition pass",
          "type": "integer",
          "context": {
            "features": "{\"feature\":{\"defaultValue\":2,\"rules\":[{\"force\":1,\"condition\":{\"country\":{\"${'$'}in\":[\"US\",\"CA\"]},\"browser\":\"firefox\"}}]}}",
            "attributes": "{\"country\":\"US\",\"browser\":\"firefox\"}",
            "forcedVariations": null
          },
          "feature": "feature",
          "result": "{\"on\":true,\"off\":false,\"source\":\"force\",\"value\":1,\"experiment\":null,\"experimentResult\":null}"
        },
        {
          "name": "force rules - condition fail",
          "type": "integer",
          "context": {
            "features": "{\"feature\":{\"defaultValue\":2,\"rules\":[{\"force\":1,\"condition\":{\"country\":{\"${'$'}in\":[\"US\",\"CA\"]},\"browser\":\"firefox\"}}]}}",
            "attributes": "{\"country\":\"US\",\"browser\":\"chrome\"}",
            "forcedVariations": null
          },
          "feature": "feature",
          "result": "{\"on\":true,\"off\":false,\"source\":\"defaultValue\",\"value\":2,\"experiment\":null,\"experimentResult\":null}"
        },
        {
          "name": "force rules - coverage with bad hash version",
          "type": "integer",
          "context": {
            "features": "{\"feature\":{\"defaultValue\":2,\"rules\":[{\"force\":1,\"coverage\":1,\"hashVersion\":99}]}}",
            "attributes": "{\"id\":\"1\"}",
            "forcedVariations": null
          },
          "feature": "feature",
          "result": "{\"on\":true,\"off\":false,\"source\":\"defaultValue\",\"value\":2,\"experiment\":null,\"experimentResult\":null}"
        },
        {
          "name": "ignores empty rules",
          "type": "null",
          "context": {
            "features": "{\"feature\":{\"rules\":[{}]}}",
            "attributes": "null",
            "forcedVariations": null
          },
          "feature": "feature",
          "result": "{\"on\":false,\"off\":true,\"source\":\"defaultValue\",\"value\":null,\"experiment\":null,\"experimentResult\":null}"
        },
        {
          "name": "empty experiment rule - c",
          "type": "string",
          "context": {
            "features": "{\"feature\":{\"rules\":[{\"variations\":[\"a\",\"b\",\"c\"]}]}}",
            "attributes": "{\"id\":\"123\"}",
            "forcedVariations": null
          },
          "feature": "feature",
          "result": "{\"on\":true,\"off\":false,\"source\":\"experiment\",\"value\":\"c\",\"experiment\":{\"key\":\"feature\",\"variations\":[\"a\",\"b\",\"c\"]},\"experimentResult\":{\"featureId\":\"feature\",\"value\":\"c\",\"variationId\":2,\"inExperiment\":true,\"hashUsed\":true,\"hashAttribute\":\"id\",\"hashValue\":\"123\",\"bucket\":0.863,\"key\":\"2\"}}"
        },
        {
          "name": "empty experiment rule - a",
          "type": "string",
          "context": {
            "features": "{\"feature\":{\"rules\":[{\"variations\":[\"a\",\"b\",\"c\"]}]}}",
            "attributes": "{\"id\":\"456\"}",
            "forcedVariations": null
          },
          "feature": "feature",
          "result": "{\"on\":true,\"off\":false,\"source\":\"experiment\",\"value\":\"a\",\"experiment\":{\"key\":\"feature\",\"variations\":[\"a\",\"b\",\"c\"]},\"experimentResult\":{\"featureId\":\"feature\",\"value\":\"a\",\"variationId\":0,\"inExperiment\":true,\"hashUsed\":true,\"hashAttribute\":\"id\",\"hashValue\":\"456\",\"bucket\":0.178,\"key\":\"0\"}}"
        },
        {
          "name": "empty experiment rule - b",
          "type": "string",
          "context": {
            "features": "{\"feature\":{\"rules\":[{\"variations\":[\"a\",\"b\",\"c\"]}]}}",
            "attributes": "{\"id\":\"fds\"}",
            "forcedVariations": null
          },
          "feature": "feature",
          "result": "{\"on\":true,\"off\":false,\"source\":\"experiment\",\"value\":\"b\",\"experiment\":{\"key\":\"feature\",\"variations\":[\"a\",\"b\",\"c\"]},\"experimentResult\":{\"featureId\":\"feature\",\"value\":\"b\",\"variationId\":1,\"inExperiment\":true,\"hashUsed\":true,\"hashAttribute\":\"id\",\"hashValue\":\"fds\",\"bucket\":0.514,\"key\":\"1\"}}"
        },
        {
          "name": "creates experiments properly",
          "type": "boolean",
          "context": {
            "features": "{\"feature\":{\"rules\":[{\"coverage\":0.99,\"hashAttribute\":\"anonId\",\"seed\":\"feature\",\"hashVersion\":2,\"name\":\"Test\",\"phase\":\"1\",\"ranges\":[[0,0.1],[0.1,1]],\"meta\":[{\"key\":\"v0\",\"name\":\"variation 0\"},{\"key\":\"v1\",\"name\":\"variation 1\"}],\"filters\":[{\"attribute\":\"anonId\",\"seed\":\"pricing\",\"ranges\":[[0,1]]}],\"namespace\":[\"pricing\",0,1],\"key\":\"hello\",\"variations\":[true,false],\"weights\":[0.1,0.9],\"condition\":{\"premium\":true}}]}}",
            "attributes": "{\"anonId\":\"123\",\"premium\":true}",
            "forcedVariations": null
          },
          "feature": "feature",
          "result": "{\"on\":false,\"off\":true,\"source\":\"experiment\",\"value\":false,\"experiment\":{\"coverage\":0.99,\"ranges\":[[0,0.1],[0.1,1]],\"meta\":[{\"key\":\"v0\",\"name\":\"variation 0\"},{\"key\":\"v1\",\"name\":\"variation 1\"}],\"filters\":[{\"attribute\":\"anonId\",\"seed\":\"pricing\",\"ranges\":[[0,1]]}],\"name\":\"Test\",\"phase\":\"1\",\"seed\":\"feature\",\"hashVersion\":2,\"hashAttribute\":\"anonId\",\"namespace\":[\"pricing\",0,1],\"key\":\"hello\",\"variations\":[true,false],\"weights\":[0.1,0.9]},\"experimentResult\":{\"featureId\":\"feature\",\"value\":false,\"variationId\":1,\"inExperiment\":true,\"hashUsed\":true,\"hashAttribute\":\"anonId\",\"hashValue\":\"123\",\"bucket\":0.5231,\"key\":\"v1\",\"name\":\"variation 1\"}}"
        },
        {
          "name": "rule orders - skip 1",
          "type": "integer",
          "context": {
            "features": "{\"feature\":{\"defaultValue\":0,\"rules\":[{\"force\":1,\"condition\":{\"browser\":\"chrome\"}},{\"force\":2,\"condition\":{\"browser\":\"firefox\"}},{\"force\":3,\"condition\":{\"browser\":\"safari\"}}]}}",
            "attributes": "{\"browser\":\"firefox\"}",
            "forcedVariations": null
          },
          "feature": "feature",
          "result": "{\"on\":true,\"off\":false,\"source\":\"force\",\"value\":2,\"experiment\":null,\"experimentResult\":null}"
        },
        {
          "name": "rule orders - skip 1,2",
          "type": "integer",
          "context": {
            "features": "{\"feature\":{\"defaultValue\":0,\"rules\":[{\"force\":1,\"condition\":{\"browser\":\"chrome\"}},{\"force\":2,\"condition\":{\"browser\":\"firefox\"}},{\"force\":3,\"condition\":{\"browser\":\"safari\"}}]}}",
            "attributes": "{\"browser\":\"safari\"}",
            "forcedVariations": null
          },
          "feature": "feature",
          "result": "{\"on\":true,\"off\":false,\"source\":\"force\",\"value\":3,\"experiment\":null,\"experimentResult\":null}"
        },
        {
          "name": "rule orders - skip all",
          "type": "integer",
          "context": {
            "features": "{\"feature\":{\"defaultValue\":0,\"rules\":[{\"force\":1,\"condition\":{\"browser\":\"chrome\"}},{\"force\":2,\"condition\":{\"browser\":\"firefox\"}},{\"force\":3,\"condition\":{\"browser\":\"safari\"}}]}}",
            "attributes": "{\"browser\":\"ie\"}",
            "forcedVariations": null
          },
          "feature": "feature",
          "result": "{\"on\":false,\"off\":true,\"source\":\"defaultValue\",\"value\":0,\"experiment\":null,\"experimentResult\":null}"
        },
        {
          "name": "skips experiment on coverage",
          "type": "integer",
          "context": {
            "features": "{\"feature\":{\"defaultValue\":0,\"rules\":[{\"variations\":[0,1,2,3],\"coverage\":0.01},{\"force\":3}]}}",
            "attributes": "{\"id\":\"123\"}",
            "forcedVariations": null
          },
          "feature": "feature",
          "result": "{\"on\":true,\"off\":false,\"source\":\"force\",\"value\":3,\"experiment\":null,\"experimentResult\":null}"
        },
        {
          "name": "skips experiment on namespace",
          "type": "integer",
          "context": {
            "features": "{\"feature\":{\"defaultValue\":0,\"rules\":[{\"variations\":[0,1,2,3],\"namespace\":[\"pricing\",0,0.01]},{\"force\":3}]}}",
            "attributes": "{\"id\":\"123\"}",
            "forcedVariations": null
          },
          "feature": "feature",
          "result": "{\"on\":true,\"off\":false,\"source\":\"force\",\"value\":3,\"experiment\":null,\"experimentResult\":null}"
        },
        {
          "name": "handles integer hashAttribute",
          "type": "integer",
          "context": {
            "features": "{\"feature\":{\"defaultValue\":0,\"rules\":[{\"variations\":[0,1]}]}}",
            "attributes": "{\"id\":123}",
            "forcedVariations": null
          },
          "feature": "feature",
          "result": "{\"on\":true,\"off\":false,\"source\":\"experiment\",\"value\":1,\"experiment\":{\"key\":\"feature\",\"variations\":[0,1]},\"experimentResult\":{\"featureId\":\"feature\",\"hashAttribute\":\"id\",\"hashValue\":123,\"hashUsed\":true,\"inExperiment\":true,\"value\":1,\"variationId\":1,\"key\":\"1\",\"bucket\":0.863}}"
        },
        {
          "name": "skip experiment on missing hashAttribute",
          "type": "integer",
          "context": {
            "features": "{\"feature\":{\"defaultValue\":0,\"rules\":[{\"variations\":[0,1,2,3],\"hashAttribute\":\"company\"},{\"force\":3}]}}",
            "attributes": "{\"id\":\"123\"}",
            "forcedVariations": null
          },
          "feature": "feature",
          "result": "{\"on\":true,\"off\":false,\"source\":\"force\",\"value\":3,\"experiment\":null,\"experimentResult\":null}"
        },
        {
          "name": "include experiments when forced",
          "type": "integer",
          "context": {
            "features": "{\"feature\":{\"defaultValue\":0,\"rules\":[{\"variations\":[0,1,2,3]},{\"force\":3}]}}",
            "attributes": "{\"id\":\"123\"}",
            "forcedVariations": {"feature": 1}
          },
          "feature": "feature",
          "result": "{\"on\":true,\"off\":false,\"source\":\"experiment\",\"value\":1,\"experiment\":{\"key\":\"feature\",\"variations\":[0,1,2,3]},\"experimentResult\":{\"featureId\":\"feature\",\"value\":1,\"variationId\":1,\"inExperiment\":true,\"hashUsed\":false,\"hashAttribute\":\"id\",\"hashValue\":\"123\",\"key\":\"1\"}}"
        },
        {
          "name": "Force rule with range, ignores coverage",
          "type": "integer",
          "context": {
            "features": "{\"feature\":{\"defaultValue\":0,\"rules\":[{\"force\":2,\"coverage\":0.01,\"range\":[0,0.99]}]}}",
            "attributes": "{\"id\":\"1\"}",
            "forcedVariations": null
          },
          "feature": "feature",
          "result": "{\"on\":true,\"off\":false,\"source\":\"force\",\"value\":2,\"experiment\":null,\"experimentResult\":null}"
        },
        {
          "name": "Force rule, hash version 2",
          "type": "integer",
          "context": {
            "features": "{\"feature\":{\"defaultValue\":0,\"rules\":[{\"force\":2,\"hashVersion\":2,\"range\":[0.96,0.97]}]}}",
            "attributes": "{\"id\":\"1\"}",
            "forcedVariations": null
          },
          "feature": "feature",
          "result": "{\"on\":true,\"off\":false,\"source\":\"force\",\"value\":2,\"experiment\":null,\"experimentResult\":null}"
        },
        {
          "name": "Force rule, skip due to range",
          "type": "integer",
          "context": {
            "features": "{\"feature\":{\"defaultValue\":0,\"rules\":[{\"force\":2,\"range\":[0,0.01]}]}}",
            "attributes": "{\"id\":\"1\"}",
            "forcedVariations": null
          },
          "feature": "feature",
          "result": "{\"on\":false,\"off\":true,\"source\":\"defaultValue\",\"value\":0,\"experiment\":null,\"experimentResult\":null}"
        },
        {
          "name": "Force rule, skip due to filter",
          "type": "integer",
          "context": {
            "features": "{\"feature\":{\"defaultValue\":0,\"rules\":[{\"force\":2,\"filters\":[{\"seed\":\"seed\",\"ranges\":[[0,0.01]]}]}]}}",
            "attributes": "{\"id\":\"1\"}",
            "forcedVariations": null
          },
          "feature": "feature",
          "result": "{\"on\":false,\"off\":true,\"source\":\"defaultValue\",\"value\":0,\"experiment\":null,\"experimentResult\":null}"
        },
        {
          "name": "Force rule, use seed with range",
          "type": "integer",
          "context": {
            "features": "{\"feature\":{\"defaultValue\":0,\"rules\":[{\"force\":2,\"range\":[0,0.5],\"seed\":\"fjdslafdsa\",\"hashVersion\":2}]}}",
            "attributes": "{\"id\":\"1\"}",
            "forcedVariations": null
          },
          "feature": "feature",
          "result": "{\"on\":true,\"off\":false,\"source\":\"force\",\"value\":2,\"experiment\":null,\"experimentResult\":null}"
        },
        {
          "name": "Support passthrough variations",
          "type": "integer",
          "context": {
            "features": "{\"feature\":{\"defaultValue\":0,\"rules\":[{\"key\":\"holdout\",\"variations\":[1,2],\"hashVersion\":2,\"ranges\":[[0,0.01],[0.01,1]],\"meta\":[{},{\"passthrough\":true}]},{\"key\":\"experiment\",\"variations\":[3,4],\"hashVersion\":2,\"ranges\":[[0,0.5],[0.5,1]]}]}}",
            "attributes": "{\"id\":\"1\"}",
            "forcedVariations": null
          },
          "feature": "feature",
          "result": "{\"on\":true,\"off\":false,\"source\":\"experiment\",\"value\":3,\"experiment\":{\"key\":\"experiment\",\"hashVersion\":2,\"variations\":[3,4],\"ranges\":[[0,0.5],[0.5,1]]},\"experimentResult\":{\"featureId\":\"feature\",\"hashAttribute\":\"id\",\"hashUsed\":true,\"hashValue\":\"1\",\"inExperiment\":true,\"key\":\"0\",\"value\":3,\"variationId\":0,\"bucket\":0.4413}}"
        },
        {
          "name": "Support holdout groups",
          "type": "integer",
          "context": {
            "features": "{\"feature\":{\"defaultValue\":0,\"rules\":[{\"key\":\"holdout\",\"hashVersion\":2,\"variations\":[1,2],\"ranges\":[[0,0.99],[0.99,1]],\"meta\":[{},{\"passthrough\":true}]},{\"key\":\"experiment\",\"hashVersion\":2,\"variations\":[3,4],\"ranges\":[[0,0.5],[0.5,1]]}]}}",
            "attributes": "{\"id\":\"1\"}",
            "forcedVariations": null
          },
          "feature": "feature",
          "result": "{\"on\":true,\"off\":false,\"source\":\"experiment\",\"value\":1,\"experiment\":{\"hashVersion\":2,\"ranges\":[[0,0.99],[0.99,1]],\"meta\":[{},{\"passthrough\":true}],\"key\":\"holdout\",\"variations\":[1,2]},\"experimentResult\":{\"featureId\":\"feature\",\"hashAttribute\":\"id\",\"hashUsed\":true,\"hashValue\":\"1\",\"inExperiment\":true,\"key\":\"0\",\"value\":1,\"variationId\":0,\"bucket\":0.8043}}"
        }
      ],
      "run": [
        {
          "name": "default weights - 1",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"1\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1]}",
          "result": {"value": 1, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "default weights - 2",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"2\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1]}",
          "result": {"value": 0, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "default weights - 3",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"3\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1]}",
          "result": {"value": 0, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "default weights - 4",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"4\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1]}",
          "result": {"value": 1, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "default weights - 5",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"5\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1]}",
          "result": {"value": 1, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "default weights - 6",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"6\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1]}",
          "result": {"value": 1, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "default weights - 7",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"7\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1]}",
          "result": {"value": 0, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "default weights - 8",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"8\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1]}",
          "result": {"value": 1, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "default weights - 9",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"9\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1]}",
          "result": {"value": 0, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "uneven weights - 1",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"1\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1],\"weights\":[0.1,0.9]}",
          "result": {"value": 1, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "uneven weights - 2",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"2\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1],\"weights\":[0.1,0.9]}",
          "result": {"value": 1, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "uneven weights - 3",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"3\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1],\"weights\":[0.1,0.9]}",
          "result": {"value": 0, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "uneven weights - 4",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"4\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1],\"weights\":[0.1,0.9]}",
          "result": {"value": 1, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "uneven weights - 5",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"5\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1],\"weights\":[0.1,0.9]}",
          "result": {"value": 1, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "uneven weights - 6",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"6\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1],\"weights\":[0.1,0.9]}",
          "result": {"value": 1, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "uneven weights - 7",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"7\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1],\"weights\":[0.1,0.9]}",
          "result": {"value": 0, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "uneven weights - 8",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"8\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1],\"weights\":[0.1,0.9]}",
          "result": {"value": 1, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "uneven weights - 9",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"9\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1],\"weights\":[0.1,0.9]}",
          "result": {"value": 1, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "coverage - 1",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"1\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1],\"coverage\":0.4}",
          "result": {"value": 0, "inExperiment": false, "hashUsed": false}
        },
        {
          "name": "coverage - 2",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"2\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1],\"coverage\":0.4}",
          "result": {"value": 0, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "coverage - 3",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"3\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1],\"coverage\":0.4}",
          "result": {"value": 0, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "coverage - 4",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"4\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1],\"coverage\":0.4}",
          "result": {"value": 0, "inExperiment": false, "hashUsed": false}
        },
        {
          "name": "coverage - 5",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"5\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1],\"coverage\":0.4}",
          "result": {"value": 1, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "coverage - 6",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"6\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1],\"coverage\":0.4}",
          "result": {"value": 0, "inExperiment": false, "hashUsed": false}
        },
        {
          "name": "coverage - 7",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"7\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1],\"coverage\":0.4}",
          "result": {"value": 0, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "coverage - 8",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"8\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1],\"coverage\":0.4}",
          "result": {"value": 1, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "coverage - 9",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"9\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1],\"coverage\":0.4}",
          "result": {"value": 0, "inExperiment": false, "hashUsed": false}
        },
        {
          "name": "three way test - 1",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"1\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1,2]}",
          "result": {"value": 2, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "three way test - 2",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"2\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1,2]}",
          "result": {"value": 0, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "three way test - 3",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"3\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1,2]}",
          "result": {"value": 0, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "three way test - 4",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"4\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1,2]}",
          "result": {"value": 2, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "three way test - 5",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"5\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1,2]}",
          "result": {"value": 1, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "three way test - 6",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"6\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1,2]}",
          "result": {"value": 2, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "three way test - 7",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"7\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1,2]}",
          "result": {"value": 0, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "three way test - 8",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"8\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1,2]}",
          "result": {"value": 1, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "three way test - 9",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"9\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1,2]}",
          "result": {"value": 0, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "test name - my-test",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"1\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1]}",
          "result": {"value": 1, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "test name - my-test-3",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"1\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test-3\",\"variations\":[0,1]}",
          "result": {"value": 0, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "empty id",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1]}",
          "result": {"value": 0, "inExperiment": false, "hashUsed": false}
        },
        {
          "name": "null id",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":null}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1]}",
          "result": {"value": 0, "inExperiment": false, "hashUsed": false}
        },
        {
          "name": "missing id",
          "type": "integer",
          "context": {
            "attributes": "{}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1]}",
          "result": {"value": 0, "inExperiment": false, "hashUsed": false}
        },
        {
          "name": "missing attributes",
          "type": "integer",
          "context": {
            "attributes": "null",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1]}",
          "result": {"value": 0, "inExperiment": false, "hashUsed": false}
        },
        {
          "name": "single variation",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"1\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0]}",
          "result": {"value": 0, "inExperiment": false, "hashUsed": false}
        },
        {
          "name": "negative forced variation",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"1\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1],\"force\":-8}",
          "result": {"value": 0, "inExperiment": false, "hashUsed": false}
        },
        {
          "name": "high forced variation",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"1\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1],\"force\":25}",
          "result": {"value": 0, "inExperiment": false, "hashUsed": false}
        },
        {
          "name": "evaluates conditions - pass",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"1\",\"browser\":\"firefox\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1],\"condition\":{\"browser\":\"firefox\"}}",
          "result": {"value": 1, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "evaluates conditions - fail",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"1\",\"browser\":\"chrome\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1],\"condition\":{\"browser\":\"firefox\"}}",
          "result": {"value": 0, "inExperiment": false, "hashUsed": false}
        },
        {
          "name": "custom hashAttribute",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"2\",\"companyId\":\"1\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1],\"hashAttribute\":\"companyId\"}",
          "result": {"value": 1, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "globally disabled",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"1\"}",
            "enabled": false,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1]}",
          "result": {"value": 0, "inExperiment": false, "hashUsed": false}
        },
        {
          "name": "querystring force",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"1\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": "http://example.com?forced-test-qs=1#someanchor"
          },
          "experiment": "{\"key\":\"forced-test-qs\",\"variations\":[0,1]}",
          "result": {"value": 1, "inExperiment": true, "hashUsed": false}
        },
        {
          "name": "run active experiments",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"1\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"active\":true,\"variations\":[0,1]}",
          "result": {"value": 1, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "skip inactive experiments",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"1\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"active\":false,\"variations\":[0,1]}",
          "result": {"value": 0, "inExperiment": false, "hashUsed": false}
        },
        {
          "name": "querystring force with inactive",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"1\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": "http://example.com/?my-test=1"
          },
          "experiment": "{\"key\":\"my-test\",\"active\":false,\"variations\":[0,1]}",
          "result": {"value": 1, "inExperiment": true, "hashUsed": false}
        },
        {
          "name": "coverage take precendence over forced",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"1\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"force\":1,\"coverage\":0.01,\"variations\":[0,1]}",
          "result": {"value": 0, "inExperiment": false, "hashUsed": false}
        },
        {
          "name": "JSON values for experiments",
          "type": "string",
          "context": {
            "attributes": "{\"id\":\"1\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[{\"color\":\"blue\",\"size\":\"small\"},{\"color\":\"green\",\"size\":\"large\"}]}",
          "result": {
            "value": {"color": "green", "size": "large"},
            "inExperiment": true,
            "hashUsed": true
          }
        },
        {
          "name": "Force variation from context",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"1\"}",
            "enabled": null,
            "forcedVariations": {"my-test": 0},
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1]}",
          "result": {"value": 0, "inExperiment": true, "hashUsed": false}
        },
        {
          "name": "Skips experiments in QA mode",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"1\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": true,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1]}",
          "result": {"value": 0, "inExperiment": false, "hashUsed": false}
        },
        {
          "name": "Works in QA mode if forced in context",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"1\"}",
            "enabled": null,
            "forcedVariations": {"my-test": 1},
            "qaMode": true,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1]}",
          "result": {"value": 1, "inExperiment": true, "hashUsed": false}
        },
        {
          "name": "Works in QA mode if forced in experiment",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"1\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": true,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1],\"force\":1}",
          "result": {"value": 1, "inExperiment": true, "hashUsed": false}
        },
        {
          "name": "Experiment namespace - pass",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"1\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1],\"namespace\":[\"namespace\",0.1,1]}",
          "result": {"value": 1, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "Experiment namespace - fail",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"1\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"my-test\",\"variations\":[0,1],\"namespace\":[\"namespace\",0,0.1]}",
          "result": {"value": 0, "inExperiment": false, "hashUsed": false}
        },
        {
          "name": "Experiment coverage - Works when 0",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"1\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"no-coverage\",\"variations\":[0,1],\"coverage\":0}",
          "result": {"value": 0, "inExperiment": false, "hashUsed": false}
        },
        {
          "name": "Filtered, included",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"1\",\"anonId\":\"fsdafsda\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"filtered\",\"variations\":[0,1],\"filters\":[{\"seed\":\"seed\",\"ranges\":[[0,0.1],[0.2,0.4]]},{\"seed\":\"seed\",\"attribute\":\"anonId\",\"ranges\":[[0.8,1]]}]}",
          "result": {"value": 1, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "Filtered, excluded",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"1\",\"anonId\":\"fsdafsda\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"filtered\",\"variations\":[0,1],\"filters\":[{\"seed\":\"seed\",\"ranges\":[[0,0.1],[0.2,0.4]]},{\"seed\":\"seed\",\"attribute\":\"anonId\",\"ranges\":[[0.6,0.8]]}]}",
          "result": {"value": 0, "inExperiment": false, "hashUsed": false}
        },
        {
          "name": "Filtered, ignore namespace",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"1\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"filtered\",\"variations\":[0,1],\"filters\":[{\"seed\":\"seed\",\"ranges\":[[0,0.1],[0.2,0.4]]}],\"namespace\":[\"test\",0,0.001]}",
          "result": {"value": 1, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "Ranges, ignore coverage and weights",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"1\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"ranges\",\"variations\":[0,1],\"ranges\":[[0.99,1],[0,0.99]],\"coverage\":0.01,\"weights\":[0.99,0.01]}",
          "result": {"value": 1, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "Ranges, partial coverage",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"1\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"configs\",\"variations\":[0,1],\"ranges\":[[0,0.1],[0.9,1]]}",
          "result": {"value": 0, "inExperiment": false, "hashUsed": false}
        },
        {
          "name": "Uses seed and hash version",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"1\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"key\",\"seed\":\"foo\",\"hashVersion\":2,\"variations\":[0,1],\"ranges\":[[0,0.5],[0.5,1]]}",
          "result": {"value": 1, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "Uses seed with default weights/coverage",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"1\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"key\",\"seed\":\"foo\",\"hashVersion\":2,\"variations\":[0,1]}",
          "result": {"value": 1, "inExperiment": true, "hashUsed": true}
        },
        {
          "name": "Uses seed with weights/coverage",
          "type": "integer",
          "context": {
            "attributes": "{\"id\":\"1\"}",
            "enabled": null,
            "forcedVariations": null,
            "qaMode": null,
            "url": null
          },
          "experiment": "{\"key\":\"key\",\"seed\":\"foo\",\"hashVersion\":2,\"variations\":[0,1],\"weights\":[0.5,0.5],\"coverage\":0.99}",
          "result": {"value": 1, "inExperiment": true, "hashUsed": true}
        }
      ],
      "evalCondition": [
        ["${"$"}not - pass", {"${"$"}not": {"name": "hello"}}, {"name": "world"}, true],
        ["${"$"}not - fail", {"${"$"}not": {"name": "hello"}}, {"name": "hello"}, false],
        [
          "${"$"}and/${"$"}or - all true",
          {
            "${"$"}and": [
              {"father.age": {"${"$"}gt": 65}},
              {"${"$"}or": [{"bday": {"${"$"}regex": "-12-25${'$'}"}}, {"name": "santa"}]}
            ]
          },
          {"name": "santa", "bday": "1980-12-25", "father": {"age": 70}},
          true
        ],
        [
          "${"$"}and/${"$"}or - first or true",
          {
            "${"$"}and": [
              {"father.age": {"${"$"}gt": 65}},
              {"${"$"}or": [{"bday": {"${"$"}regex": "-12-25${'$'}"}}, {"name": "santa"}]}
            ]
          },
          {"name": "santa", "bday": "1980-12-20", "father": {"age": 70}},
          true
        ],
        [
          "${"$"}and/${"$"}or - second or true",
          {
            "${"$"}and": [
              {"father.age": {"${"$"}gt": 65}},
              {"${"$"}or": [{"bday": {"${"$"}regex": "-12-25${'$'}"}}, {"name": "santa"}]}
            ]
          },
          {"name": "barbara", "bday": "1980-12-25", "father": {"age": 70}},
          true
        ],
        [
          "${"$"}and/${"$"}or - first and false",
          {
            "${"$"}and": [
              {"father.age": {"${"$"}gt": 65}},
              {"${"$"}or": [{"bday": {"${"$"}regex": "-12-25${'$'}"}}, {"name": "santa"}]}
            ]
          },
          {"name": "santa", "bday": "1980-12-25", "father": {"age": 65}},
          false
        ],
        [
          "${"$"}and/${"$"}or - both or false",
          {
            "${"$"}and": [
              {"father.age": {"${"$"}gt": 65}},
              {"${"$"}or": [{"bday": {"${"$"}regex": "-12-25${'$'}"}}, {"name": "santa"}]}
            ]
          },
          {"name": "barbara", "bday": "1980-11-25", "father": {"age": 70}},
          false
        ],
        [
          "${"$"}and/${"$"}or - both and false",
          {
            "${"$"}and": [
              {"father.age": {"${"$"}gt": 65}},
              {"${"$"}or": [{"bday": {"${"$"}regex": "-12-25${'$'}"}}, {"name": "santa"}]}
            ]
          },
          {"name": "john smith", "bday": "1956-12-20", "father": {"age": 40}},
          false
        ],
        [
          "${"$"}exists - false pass",
          {"pets.dog.name": {"${"$"}exists": false}},
          {"hello": "world"},
          true
        ],
        [
          "${"$"}exists - false fail",
          {"pets.dog.name": {"${"$"}exists": false}},
          {"pets": {"dog": {"name": "fido"}}},
          false
        ],
        [
          "${"$"}exists - true fail",
          {"pets.dog.name": {"${"$"}exists": true}},
          {"hello": "world"},
          false
        ],
        [
          "${"$"}exists - true pass",
          {"pets.dog.name": {"${"$"}exists": true}},
          {"pets": {"dog": {"name": "fido"}}},
          true
        ],
        [
          "equals - multiple datatypes",
          {"str": "str", "num": 10, "flag": false},
          {"str": "str", "num": 10, "flag": false},
          true
        ],
        ["${'$'}in - pass", {"num": {"${'$'}in": [1, 2, 3]}}, {"num": 2}, true],
        ["${'$'}in - fail", {"num": {"${'$'}in": [1, 2, 3]}}, {"num": 4}, false],
        ["${'$'}in - not array", {"num": {"${'$'}in": 1}}, {"num": 1}, false],
        [
          "${'$'}in - array pass 1",
          {"tags": {"${'$'}in": ["a", "b"]}},
          {"tags": ["d", "e", "a"]},
          true
        ],
        [
          "${'$'}in - array pass 2",
          {"tags": {"${'$'}in": ["a", "b"]}},
          {"tags": ["d", "b", "f"]},
          true
        ],
        [
          "${'$'}in - array pass 3",
          {"tags": {"${'$'}in": ["a", "b"]}},
          {"tags": ["d", "b", "a"]},
          true
        ],
        [
          "${'$'}in - array fail 1",
          {"tags": {"${'$'}in": ["a", "b"]}},
          {"tags": ["d", "e", "f"]},
          false
        ],
        ["${'$'}in - array fail 2", {"tags": {"${'$'}in": ["a", "b"]}}, {"tags": []}, false],
        ["${"$"}nin - pass", {"num": {"${"$"}nin": [1, 2, 3]}}, {"num": 4}, true],
        ["${"$"}nin - fail", {"num": {"${"$"}nin": [1, 2, 3]}}, {"num": 2}, false],
        ["${"$"}nin - not array", {"num": {"${"$"}nin": 1}}, {"num": 1}, false],
        [
          "${"$"}nin - array fail 1",
          {"tags": {"${"$"}nin": ["a", "b"]}},
          {"tags": ["d", "e", "a"]},
          false
        ],
        [
          "${"$"}nin - array fail 2",
          {"tags": {"${"$"}nin": ["a", "b"]}},
          {"tags": ["d", "b", "f"]},
          false
        ],
        [
          "${"$"}nin - array fail 3",
          {"tags": {"${"$"}nin": ["a", "b"]}},
          {"tags": ["d", "b", "a"]},
          false
        ],
        [
          "${"$"}nin - array pass 1",
          {"tags": {"${"$"}nin": ["a", "b"]}},
          {"tags": ["d", "e", "f"]},
          true
        ],
        ["${"$"}nin - array pass 2", {"tags": {"${"$"}nin": ["a", "b"]}}, {"tags": []}, true],
        [
          "${"$"}elemMatch - pass - flat arrays",
          {"nums": {"${"$"}elemMatch": {"${"$"}gt": 10}}},
          {"nums": [0, 5, -20, 15]},
          true
        ],
        [
          "${"$"}elemMatch - fail - flat arrays",
          {"nums": {"${"$"}elemMatch": {"${"$"}gt": 10}}},
          {"nums": [0, 5, -20, 8]},
          false
        ],
        [
          "missing attribute - fail",
          {"pets.dog.name": {"${'$'}in": ["fido"]}},
          {"hello": "world"},
          false
        ],
        ["empty ${"$"}or - pass", {"${"$"}or": []}, {"hello": "world"}, true],
        ["empty ${"$"}and - pass", {"${"$"}and": []}, {"hello": "world"}, true],
        ["empty - pass", {}, {"hello": "world"}, true],
        [
          "${"$"}eq - pass",
          {"occupation": {"${"$"}eq": "engineer"}},
          {"occupation": "engineer"},
          true
        ],
        [
          "${"$"}eq - fail",
          {"occupation": {"${"$"}eq": "engineer"}},
          {"occupation": "civil engineer"},
          false
        ],
        ["${"$"}ne - pass", {"level": {"${"$"}ne": "senior"}}, {"level": "junior"}, true],
        ["${"$"}ne - fail", {"level": {"${"$"}ne": "senior"}}, {"level": "senior"}, false],
        [
          "${"$"}regex - pass",
          {"userAgent": {"${"$"}regex": "(Mobile|Tablet)"}},
          {"userAgent": "Android Mobile Browser"},
          true
        ],
        [
          "${"$"}regex - fail",
          {"userAgent": {"${"$"}regex": "(Mobile|Tablet)"}},
          {"userAgent": "Chrome Desktop Browser"},
          false
        ],
        [
          "${"$"}gt/${"$"}lt numbers - pass",
          {"age": {"${"$"}gt": 30, "${"$"}lt": 60}},
          {"age": 50},
          true
        ],
        [
          "${"$"}gt/${"$"}lt numbers - fail ${"$"}lt",
          {"age": {"${"$"}gt": 30, "${"$"}lt": 60}},
          {"age": 60},
          false
        ],
        [
          "${"$"}gt/${"$"}lt numbers - fail ${"$"}gt",
          {"age": {"${"$"}gt": 30, "${"$"}lt": 60}},
          {"age": 30},
          false
        ],
        [
          "${"$"}gte/${"$"}lte numbers - pass",
          {"age": {"${"$"}gte": 30, "${"$"}lte": 60}},
          {"age": 50},
          true
        ],
        [
          "${"$"}gte/${"$"}lte numbers - pass ${"$"}gte",
          {"age": {"${"$"}gte": 30, "${"$"}lte": 60}},
          {"age": 30},
          true
        ],
        [
          "${"$"}gte/${"$"}lte numbers - pass ${"$"}lte",
          {"age": {"${"$"}gte": 30, "${"$"}lte": 60}},
          {"age": 60},
          true
        ],
        [
          "${"$"}gte/${"$"}lte numbers - fail ${"$"}lte",
          {"age": {"${"$"}gte": 30, "${"$"}lte": 60}},
          {"age": 61},
          false
        ],
        [
          "${"$"}gte/${"$"}lte numbers - fail ${"$"}gte",
          {"age": {"${"$"}gt": 30, "${"$"}lt": 60}},
          {"age": 29},
          false
        ],
        [
          "${"$"}gt/${"$"}lt strings - fail ${"$"}gt",
          {"word": {"${"$"}gt": "alphabet", "${"$"}lt": "zebra"}},
          {"word": "alphabet"},
          false
        ],
        [
          "${"$"}gt/${"$"}lt strings - fail ${"$"}lt",
          {"word": {"${"$"}gt": "alphabet", "${"$"}lt": "zebra"}},
          {"word": "zebra"},
          false
        ],
        [
          "${"$"}gt/${"$"}lt strings - pass",
          {"word": {"${"$"}gt": "alphabet", "${"$"}lt": "zebra"}},
          {"word": "always"},
          true
        ],
        [
          "${"$"}gt/${"$"}lt strings - fail uppercase",
          {"word": {"${"$"}gt": "alphabet", "${"$"}lt": "zebra"}},
          {"word": "AZL"},
          false
        ],
        ["nested value is null", {"address.state": "CA"}, {"address": null}, false],
        [
          "nested value is integer",
          {"address.state": "CA"},
          {"address": 123},
          false
        ],
        ["${"$"}type string - pass", {"a": {"${"$"}type": "string"}}, {"a": "a"}, true],
        ["${"$"}type string - fail", {"a": {"${"$"}type": "string"}}, {"a": 1}, false],
        ["${"$"}type null - pass", {"a": {"${"$"}type": "null"}}, {"a": null}, true],
        ["${"$"}type null - fail", {"a": {"${"$"}type": "null"}}, {"a": 1}, false],
        ["${"$"}type boolean - pass", {"a": {"${"$"}type": "boolean"}}, {"a": false}, true],
        ["${"$"}type boolean - fail", {"a": {"${"$"}type": "boolean"}}, {"a": 1}, false],
        ["${"$"}type number - pass", {"a": {"${"$"}type": "number"}}, {"a": 1}, true],
        ["${"$"}type number - fail", {"a": {"${"$"}type": "number"}}, {"a": "a"}, false],
        [
          "${"$"}type object - pass",
          {"a": {"${"$"}type": "object"}},
          {"a": {"a": "b"}},
          true
        ],
        ["${"$"}type object - fail", {"a": {"${"$"}type": "object"}}, {"a": 1}, false],
        ["${"$"}type array - pass", {"a": {"${"$"}type": "array"}}, {"a": [1, 2]}, true],
        ["${"$"}type array - fail", {"a": {"${"$"}type": "array"}}, {"a": 1}, false],
        [
          "unknown operator - pass",
          {"name": {"${"$"}regx": "hello"}},
          {"name": "hello"},
          false
        ],
        [
          "${"$"}regex invalid - pass",
          {"name": {"${"$"}regex": "/???***[)"}},
          {"name": "hello"},
          false
        ],
        [
          "${"$"}regex invalid - fail",
          {"name": {"${"$"}regex": "/???***[)"}},
          {"hello": "hello"},
          false
        ],
        ["${"$"}size empty - pass", {"tags": {"${"$"}size": 0}}, {"tags": []}, true],
        ["${"$"}size empty - fail", {"tags": {"${"$"}size": 0}}, {"tags": [10]}, false],
        [
          "${"$"}size number - pass",
          {"tags": {"${"$"}size": 3}},
          {"tags": ["a", "b", "c"]},
          true
        ],
        [
          "${"$"}size number - fail small",
          {"tags": {"${"$"}size": 3}},
          {"tags": ["a", "b"]},
          false
        ],
        [
          "${"$"}size number - fail large",
          {"tags": {"${"$"}size": 3}},
          {"tags": ["a", "b", "c", "d"]},
          false
        ],
        [
          "${"$"}size number - fail not array",
          {"tags": {"${"$"}size": 3}},
          {"tags": "abc"},
          false
        ],
        [
          "${"$"}size nested - pass",
          {"tags": {"${"$"}size": {"${"$"}gt": 2}}},
          {"tags": [0, 1, 2]},
          true
        ],
        [
          "${"$"}size nested - fail equal",
          {"tags": {"${"$"}size": {"${"$"}gt": 2}}},
          {"tags": [0, 1]},
          false
        ],
        [
          "${"$"}size nested - fail less than",
          {"tags": {"${"$"}size": {"${"$"}gt": 2}}},
          {"tags": [0]},
          false
        ],
        [
          "${"$"}elemMatch contains - pass",
          {"tags": {"${"$"}elemMatch": {"${"$"}eq": "bar"}}},
          {"tags": ["foo", "bar", "baz"]},
          true
        ],
        [
          "${"$"}elemMatch contains - false",
          {"tags": {"${"$"}elemMatch": {"${"$"}eq": "bar"}}},
          {"tags": ["foo", "baz"]},
          false
        ],
        [
          "${"$"}elemMatch intersection - pass",
          {"tags": {"${"$"}elemMatch": {"${'$'}in": ["a", "b"]}}},
          {"tags": ["d", "e", "b"]},
          true
        ],
        [
          "${"$"}elemMatch intersection - fail",
          {"tags": {"${"$"}elemMatch": {"${'$'}in": ["a", "b"]}}},
          {"tags": ["d", "e", "f"]},
          false
        ],
        [
          "${"$"}elemMatch not contains - pass",
          {"tags": {"${"$"}not": {"${"$"}elemMatch": {"${"$"}eq": "bar"}}}},
          {"tags": ["foo", "baz"]},
          true
        ],
        [
          "${"$"}elemMatch not contains - fail",
          {"tags": {"${"$"}not": {"${"$"}elemMatch": {"${"$"}eq": "bar"}}}},
          {"tags": ["foo", "bar", "baz"]},
          false
        ],
        [
          "${"$"}elemMatch nested - pass",
          {"hobbies": {"${"$"}elemMatch": {"name": {"${"$"}regex": "^ping"}}}},
          {
            "hobbies": [
              {"name": "bowling"},
              {"name": "pingpong"},
              {"name": "tennis"}
            ]
          },
          true
        ],
        [
          "${"$"}elemMatch nested - fail",
          {"hobbies": {"${"$"}elemMatch": {"name": {"${"$"}regex": "^ping"}}}},
          {"hobbies": [{"name": "bowling"}, {"name": "tennis"}]},
          false
        ],
        [
          "${"$"}elemMatch nested - fail not array",
          {"hobbies": {"${"$"}elemMatch": {"name": {"${"$"}regex": "^ping"}}}},
          {"hobbies": "all"},
          false
        ],
        [
          "${"$"}not - pass",
          {"name": {"${"$"}not": {"${"$"}regex": "^hello"}}},
          {"name": "world"},
          true
        ],
        [
          "${"$"}not - fail",
          {"name": {"${"$"}not": {"${"$"}regex": "^hello"}}},
          {"name": "hello world"},
          false
        ],
        [
          "${"$"}all - pass",
          {"tags": {"${"$"}all": ["one", "three"]}},
          {"tags": ["one", "two", "three"]},
          true
        ],
        [
          "${"$"}all - fail",
          {"tags": {"${"$"}all": ["one", "three"]}},
          {"tags": ["one", "two", "four"]},
          false
        ],
        [
          "${"$"}all - fail not array",
          {"tags": {"${"$"}all": ["one", "three"]}},
          {"tags": "hello"},
          false
        ],
        [
          "${"$"}nor - pass",
          {"${"$"}nor": [{"name": "john"}, {"age": {"${"$"}lt": 30}}]},
          {"name": "jim", "age": 40},
          true
        ],
        [
          "${"$"}nor - fail both",
          {"${"$"}nor": [{"name": "john"}, {"age": {"${"$"}lt": 30}}]},
          {"name": "john", "age": 20},
          false
        ],
        [
          "${"$"}nor - fail first",
          {"${"$"}nor": [{"name": "john"}, {"age": {"${"$"}lt": 30}}]},
          {"name": "john", "age": 40},
          false
        ],
        [
          "${"$"}nor - fail second",
          {"${"$"}nor": [{"name": "john"}, {"age": {"${"$"}lt": 30}}]},
          {"name": "jim", "age": 20},
          false
        ],
        [
          "equals array - pass",
          {"tags": ["hello", "world"]},
          {"tags": ["hello", "world"]},
          true
        ],
        [
          "equals array - fail order",
          {"tags": ["hello", "world"]},
          {"tags": ["world", "hello"]},
          false
        ],
        [
          "equals array - fail missing item",
          {"tags": ["hello", "world"]},
          {"tags": ["hello"]},
          false
        ],
        [
          "equals array - fail extra item",
          {"tags": ["hello", "world"]},
          {"tags": ["hello", "world", "foo"]},
          false
        ],
        [
          "equals array - fail type mismatch",
          {"tags": ["hello", "world"]},
          {"tags": "hello world"},
          false
        ],
        [
          "equals object - pass",
          {"tags": {"hello": "world"}},
          {"tags": {"hello": "world"}},
          true
        ],
        [
          "equals object - fail extra property",
          {"tags": {"hello": "world"}},
          {"tags": {"hello": "world", "yes": "please"}},
          false
        ],
        [
          "equals object - fail missing property",
          {"tags": {"hello": "world"}},
          {"tags": {}},
          false
        ],
        [
          "equals object - fail type mismatch",
          {"tags": {"hello": "world"}},
          {"tags": "hello world"},
          false
        ],
        [
          "null condition - null attribute",
          {"userId": null},
          {"userId": null},
          true
        ],
        ["null condition - missing attribute", {"userId": null}, {}, true],
        [
          "null condition - string attribute",
          {"userId": null},
          {"userId": "123"},
          false
        ],
        ["null condition - zero attribute", {"userId": null}, {"userId": 0}, false],
        [
          "null condition - empty string attribute",
          {"userId": null},
          {"userId": ""},
          false
        ],
        [
          "${"$"}vgt/${"$"}vlt - pass - major",
          {"version": {"${"$"}vgt": "9.99.8", "${"$"}vlt": "11.0.1"}},
          {"version": "10.12.13"},
          true
        ],
        [
          "${"$"}vgt/${"$"}vlt - pass - minor",
          {"version": {"${"$"}vgt": "10.2.11", "${"$"}vlt": "10.20.11"}},
          {"version": "10.12.11"},
          true
        ],
        [
          "${"$"}vgt/${"$"}vlt - pass - patch",
          {"version": {"${"$"}vgt": "10.0.2", "${"$"}vlt": "10.0.20"}},
          {"version": "10.0.12"},
          true
        ],
        [
          "${"$"}vgt/${"$"}vlt - fail ${"$"}vlt - major",
          {"version": {"${"$"}vgt": "30.0.0", "${"$"}vlt": "50.0.0"}},
          {"version": "60.0.0"},
          false
        ],
        [
          "${"$"}vgt/${"$"}vlt - fail ${"$"}vlt - minor",
          {"version": {"${"$"}vgt": "10.30.0", "${"$"}vlt": "10.50.0"}},
          {"version": "10.60.0"},
          false
        ],
        [
          "${"$"}vgt/${"$"}vlt - fail ${"$"}vlt - patch",
          {"version": {"${"$"}vgt": "10.2.30", "${"$"}vlt": "10.2.50"}},
          {"version": "10.2.60"},
          false
        ],
        [
          "${"$"}vgt/${"$"}vlt - fail ${"$"}vgt - major",
          {"version": {"${"$"}vgt": "30.0.16", "${"$"}vlt": "50.0.16"}},
          {"version": "20.0.16"},
          false
        ],
        [
          "${"$"}vgt/${"$"}vlt - fail ${"$"}vgt - minor",
          {"version": {"${"$"}vgt": "10.30.0", "${"$"}vlt": "10.50.0"}},
          {"version": "10.20.0"},
          false
        ],
        [
          "${"$"}vgt/${"$"}vlt - fail ${"$"}vgt - patch",
          {"version": {"${"$"}vgt": "10.30.10", "${"$"}vlt": "10.30.20"}},
          {"version": "10.30.2"},
          false
        ],
        [
          "${"$"}vgte/${"$"}vlte - pass ${"$"}vgte - major",
          {"version": {"${"$"}vgte": "30.1.2", "${"$"}vlte": "60.1.2"}},
          {"version": "30.1.2"},
          true
        ],
        [
          "${"$"}vgte/${"$"}vlte - pass ${"$"}vgte - minor",
          {"version": {"${"$"}vgte": "5.30.2", "${"$"}vlte": "5.60.2"}},
          {"version": "5.30.2"},
          true
        ],
        [
          "${"$"}vgte/${"$"}vlte - pass ${"$"}vgte - patch",
          {"version": {"${"$"}vgte": "5.10.30", "${"$"}vlte": "5.10.60"}},
          {"version": "5.10.30"},
          true
        ],
        [
          "${"$"}vgte/${"$"}vlte - pass ${"$"}vlte - major",
          {"version": {"${"$"}vgte": "30.1.2", "${"$"}vlte": "60.1.2"}},
          {"version": "60.1.2"},
          true
        ],
        [
          "${"$"}vgte/${"$"}vlte - pass ${"$"}vlte - minor",
          {"version": {"${"$"}vgte": "1.30.2", "${"$"}vlte": "1.60.2"}},
          {"version": "1.60.2"},
          true
        ],
        [
          "${"$"}vgte/${"$"}vlte - pass ${"$"}vlte - patch",
          {"version": {"${"$"}vgte": "1.2.30", "${"$"}vlte": "1.2.60"}},
          {"version": "1.2.60"},
          true
        ],
        [
          "${"$"}vgte/${"$"}vlte - fail ${"$"}vlte - major",
          {"version": {"${"$"}vgte": "30.1.2", "${"$"}vlte": "60.1.2"}},
          {"version": "61.1.2"},
          false
        ],
        [
          "${"$"}vgte/${"$"}vlte - fail ${"$"}vgt - minor",
          {"version": {"${"$"}vgte": "30.1.2", "${"$"}vlte": "60.1.2"}},
          {"version": "29.1.2"},
          false
        ],
        [
          "${"$"}vgte/${"$"}vlte - fail ${"$"}vgt - patch",
          {"version": {"${"$"}vgte": "1.2.30", "${"$"}vlte": "1.2.60"}},
          {"version": "1.2.29"},
          false
        ],
        [
          "${"$"}vgt/${"$"}vlt prerelease - fail ${"$"}vgt",
          {"v": {"${"$"}vgt": "1.0.0-alpha", "${"$"}vlt": "1.0.0-beta"}},
          {"v": "1.0.0-alpha"},
          false
        ],
        [
          "${"$"}vgt/${"$"}vlt prerelease  w/ multiple fields - fail ${"$"}vgt",
          {"v": {"${"$"}vgt": "1.0.0-alpha.2", "${"$"}vlt": "1.0.0-beta.1"}},
          {"v": "1.0.0-alpha.1"},
          false
        ],
        [
          "${"$"}vgt/${"$"}vlt prerelease - fail ${"$"}vlt",
          {"v": {"${"$"}vgt": "1.0.0-alpha", "${"$"}vlt": "1.0.0-beta"}},
          {"v": "1.0.0-beta"},
          false
        ],
        [
          "${"$"}vgt/${"$"}vlt prerelease - pass",
          {"v": {"${"$"}vgt": "1.0.0-alpha", "${"$"}vlt": "1.0.0-beta"}},
          {"v": "1.0.0-alpha.10"},
          true
        ],
        [
          "${"$"}vgt/${"$"}vlt prerelease - fail uppercase",
          {"v": {"${"$"}vgt": "1.0.0-alpha", "${"$"}vlt": "1.0.0-beta"}},
          {"v": "1.0.0-ALPHA"},
          false
        ],
        ["${"$"}veq - pass", {"v": {"${"$"}veq": "1.2.3"}}, {"v": "1.2.3"}, true],
        [
          "${"$"}veq - pass (with build)",
          {"v": {"${"$"}veq": "1.2.3"}},
          {"v": "1.2.3+build.abc.123"},
          true
        ],
        ["${"$"}vne - pass", {"v": {"${"$"}vne": "1.2.3"}}, {"v": "2.2.3"}, true],
        [
          "${"$"}vne - pass (prerelease)",
          {"v": {"${"$"}vne": "1.2.3"}},
          {"v": "1.2.3-alpha"},
          true
        ]
      ],
      "versionCompare": {
        "lt": [
          ["0.9.99", "1.0.0", true],
          ["0.9.0", "0.10.0", true],
          ["1.0.0-0.0", "1.0.0-0.0.0", true],
          ["1.0.0-9999", "1.0.0--", true],
          ["1.0.0-99", "1.0.0-100", true],
          ["1.0.0-alpha", "1.0.0-alpha.1", true],
          ["1.0.0-alpha.1", "1.0.0-alpha.beta", true],
          ["1.0.0-alpha.beta", "1.0.0-beta", true],
          ["1.0.0-beta", "1.0.0-beta.2", true],
          ["1.0.0-beta.2", "1.0.0-beta.11", true],
          ["1.0.0-beta.11", "1.0.0-rc.1", true],
          ["1.0.0-rc.1", "1.0.0", true],
          ["1.0.0-0", "1.0.0--1", true],
          ["1.0.0-0", "1.0.0-1", true],
          ["1.0.0-1.0", "1.0.0-1.-1", true]
        ],
        "gt": [
          ["0.0.0", "0.0.0-foo", true],
          ["0.0.1", "0.0.0", true],
          ["1.0.0", "0.9.9", true],
          ["0.10.0", "0.9.0", true],
          ["0.99.0", "0.10.0", true],
          ["2.0.0", "1.2.3", true],
          ["v0.0.0", "0.0.0-foo", true],
          ["v0.0.1", "0.0.0", true],
          ["v1.0.0", "0.9.9", true],
          ["v0.10.0", "0.9.0", true],
          ["v0.99.0", "0.10.0", true],
          ["v2.0.0", "1.2.3", true],
          ["0.0.0", "v0.0.0-foo", true],
          ["0.0.1", "v0.0.0", true],
          ["1.0.0", "v0.9.9", true],
          ["0.10.0", "v0.9.0", true],
          ["0.99.0", "v0.10.0", true],
          ["2.0.0", "v1.2.3", true],
          ["1.2.3", "1.2.3-asdf", true],
          ["1.2.3", "1.2.3-4", true],
          ["1.2.3", "1.2.3-4-foo", true],
          ["1.2.3-5-foo", "1.2.3-5", true],
          ["1.2.3-5", "1.2.3-4", true],
          ["1.2.3-5-foo", "1.2.3-5-Foo", true],
          ["3.0.0", "2.7.2+asdf", true],
          ["1.2.3-a.10", "1.2.3-a.5", true],
          ["1.2.3-a.b", "1.2.3-a.5", true],
          ["1.2.3-a.b", "1.2.3-a", true],
          ["1.2.3-a.b.c", "1.2.3-a.b.c.d", false],
          ["1.2.3-a.b.c.10.d.5", "1.2.3-a.b.c.5.d.100", true],
          ["1.2.3-r2", "1.2.3-r100", true],
          ["1.2.3-r100", "1.2.3-R2", true],
          ["a.b.c.d.e.f", "1.2.3", true],
          ["10.0.0", "9.0.0", true],
          ["10000.0.0", "9999.0.0", true]
        ],
        "eq": [
          ["1.2.3", "1.2.3", true],
          ["1.2.3", "v1.2.3", true],
          ["1.2.3-0", "v1.2.3-0", true],
          ["1.2.3-1", "1.2.3-1", true],
          ["1.2.3-1", "v1.2.3-1", true],
          ["1.2.3-beta", "1.2.3-beta", true],
          ["1.2.3-beta", "v1.2.3-beta", true],
          ["1.2.3-beta+build", "1.2.3-beta+otherbuild", true],
          ["1.2.3-beta+build", "v1.2.3-beta+otherbuild", true],
          ["1-2-3", "1.2.3", true],
          ["1-2-3", "1-2.3+build99", true],
          ["1-2-3", "v1.2.3", true],
          ["1.2.3.4", "1.2.3-4", true]
        ]
      },
      "hash": [
        ["", "a", 1, 0.22],
        ["", "b", 1, 0.077],
        ["b", "a", 1, 0.946],
        ["ef", "d", 1, 0.652],
        ["asdf", "8952klfjas09ujk", 1, 0.549],
        ["", "123", 1, 0.011],
        ["", "___)((*\":&", 1, 0.563],
        ["seed", "a", 2, 0.0505],
        ["seed", "b", 2, 0.2696],
        ["foo", "ab", 2, 0.2575],
        ["foo", "def", 2, 0.2019],
        ["89123klj", "8952klfjas09ujkasdf", 2, 0.124],
        ["90850943850283058242805", "123", 2, 0.7516],
        ["()**(%${'$'}##${'$'}%#${'$'}#", "___)((*\":&", 2, 0.0128],
        ["abc", "def", 99, null]
      ],
      "getBucketRange": [
        ["normal 50/50", [2, 1, null], [[0, 0.5], [0.5, 1]]],
        ["reduced coverage", [2, 0.5, null], [[0, 0.25], [0.5, 0.75]]],
        ["zero coverage", [2, 0, null], [[0, 0], [0.5, 0.5]]],
        [
          "4 variations",
          [4, 1, null],
          [[0, 0.25], [0.25, 0.5], [0.5, 0.75], [0.75, 1]]
        ],
        ["uneven weights", [2, 1, [0.4, 0.6]], [[0, 0.4], [0.4, 1]]],
        [
          "uneven weights, 3 variations",
          [3, 1, [0.2, 0.3, 0.5]],
          [[0, 0.2], [0.2, 0.5], [0.5, 1]]
        ],
        [
          "uneven weights, reduced coverage, 3 variations",
          [3, 0.2, [0.2, 0.3, 0.5]],
          [[0, 0.04], [0.2, 0.26], [0.5, 0.6]]
        ],
        ["negative coverage", [2, -0.2, null], [[0, 0], [0.5, 0.5]]],
        ["coverage above 1", [2, 1.5, null], [[0, 0.5], [0.5, 1]]],
        ["weights sum below 1", [2, 1, [0.4, 0.1]], [[0, 0.5], [0.5, 1]]],
        ["weights sum above 1", [2, 1, [0.7, 0.6]], [[0, 0.5], [0.5, 1]]],
        [
          "weights.length not equal to num variations",
          [4, 1, [0.4, 0.4, 0.2]],
          [[0, 0.25], [0.25, 0.5], [0.5, 0.75], [0.75, 1]]
        ],
        [
          "weights sum almost equals 1",
          [2, 1, [0.4, 0.5999]],
          [[0, 0.4], [0.4, 0.9999]]
        ]
      ],
      "chooseVariation": [
        ["even range, 0.2", 0.2, [[0, 0.5], [0.5, 1]], 0],
        ["even range, 0.4", 0.4, [[0, 0.5], [0.5, 1]], 0],
        ["even range, 0.6", 0.6, [[0, 0.5], [0.5, 1]], 1],
        ["even range, 0.8", 0.8, [[0, 0.5], [0.5, 1]], 1],
        ["even range, 0", 0, [[0, 0.5], [0.5, 1]], 0],
        ["even range, 0.5", 0.5, [[0, 0.5], [0.5, 1]], 1],
        ["reduced range, 0.2", 0.2, [[0, 0.25], [0.5, 0.75]], 0],
        ["reduced range, 0.4", 0.4, [[0, 0.25], [0.5, 0.75]], -1],
        ["reduced range, 0.6", 0.6, [[0, 0.25], [0.5, 0.75]], 1],
        ["reduced range, 0.8", 0.8, [[0, 0.25], [0.5, 0.75]], -1],
        ["reduced range, 0.25", 0.25, [[0, 0.25], [0.5, 0.75]], -1],
        ["reduced range, 0.5", 0.5, [[0, 0.25], [0.5, 0.75]], 1],
        ["zero range", 0.5, [[0, 0.5], [0.5, 0.5], [0.5, 1]], 2]
      ],
      "getQueryStringOverride": [
        ["empty url", "my-test", "", 2, null],
        ["no query string", "my-test", "http://example.com", 2, null],
        ["empty query string", "my-test", "http://example.com?", 2, null],
        [
          "no query string match",
          "my-test",
          "http://example.com?somequery",
          2,
          null
        ],
        ["invalid query string", "my-test", "http://example.com??&&&?#", 2, null],
        ["simple match 0", "my-test", "http://example.com?my-test=0", 2, 0],
        ["simple match 1", "my-test", "http://example.com?my-test=1", 2, 1],
        ["negative variation", "my-test", "http://example.com?my-test=-1", 2, null],
        ["float", "my-test", "http://example.com?my-test=2.054", 2, null],
        ["string", "my-test", "http://example.com?my-test=foo", 2, null],
        ["variation too high", "my-test", "http://example.com?my-test=5", 2, null],
        ["high numVariations", "my-test", "http://example.com?my-test=5", 6, 5],
        [
          "equal to numVariations",
          "my-test",
          "http://example.com?my-test=5",
          5,
          null
        ],
        [
          "other query string before",
          "my-test",
          "http://example.com?foo=bar&my-test=1",
          2,
          1
        ],
        [
          "other query string after",
          "my-test",
          "http://example.com?foo=bar&my-test=1&bar=baz",
          2,
          1
        ],
        ["anchor", "my-test", "http://example.com?my-test=1#foo", 2, 1]
      ],
      "inNamespace": [
        ["user 1, namespace1, 1", "1", ["namespace1", 0, 0.4], false],
        ["user 1, namespace1, 2", "1", ["namespace1", 0.4, 1], true],
        ["user 1, namespace2, 1", "1", ["namespace2", 0, 0.4], false],
        ["user 1, namespace2, 2", "1", ["namespace2", 0.4, 1], true],
        ["user 2, namespace1, 1", "2", ["namespace1", 0, 0.4], false],
        ["user 2, namespace1, 2", "2", ["namespace1", 0.4, 1], true],
        ["user 2, namespace2, 1", "2", ["namespace2", 0, 0.4], false],
        ["user 2, namespace2, 2", "2", ["namespace2", 0.4, 1], true],
        ["user 3, namespace1, 1", "3", ["namespace1", 0, 0.4], false],
        ["user 3, namespace1, 2", "3", ["namespace1", 0.4, 1], true],
        ["user 3, namespace2, 1", "3", ["namespace2", 0, 0.4], true],
        ["user 3, namespace2, 2", "3", ["namespace2", 0.4, 1], false],
        ["user 4, namespace1, 1", "4", ["namespace1", 0, 0.4], false],
        ["user 4, namespace1, 2", "4", ["namespace1", 0.4, 1], true],
        ["user 4, namespace2, 1", "4", ["namespace2", 0, 0.4], true],
        ["user 4, namespace2, 2", "4", ["namespace2", 0.4, 1], false]
      ],
      "getEqualWeights": [
        [-1, []],
        [0, []],
        [1, [1]],
        [2, [0.5, 0.5]],
        [3, [0.33333333, 0.33333333, 0.33333333]],
        [4, [0.25, 0.25, 0.25, 0.25]]
      ],
      "decrypt": [
        [
          "Valid feature",
          "m5ylFM6ndyOJA2OPadubkw==.Uu7ViqgKEt/dWvCyhI46q088PkAEJbnXKf3KPZjf9IEQQ+A8fojNoxw4wIbPX3aj",
          "Zvwv/+uhpFDznZ6SX28Yjg==",
          "{\"feature\":{\"defaultValue\":true}}"
        ],
        [
          "Broken JSON",
          "SVZIM2oKD1JoHNIeeoW3Uw==.AGbRiGAHf2f6/ziVr9UTIy+bVFmVli6+bHZ2jnCm9N991ITv1ROvOEjxjLSmgEpv",
          "UQD0Qqw7fM1bhfKKPH8TGw==",
          "{\"feature\":{\"defaultValue\":true?5%"
        ],
        [
          "Wrong key",
          "m5ylFM6ndyOJA2OPadubkw==.Uu7ViqgKEt/dWvCyhI46q088PkAEJbnXKf3KPZjf9IEQQ+A8fojNoxw4wIbPX3aj",
          "Zvwv/+uhpFDznZ6SX39Yjg==",
          null
        ],
        [
          "Invalid key length",
          "m5ylFM6ndyOJA2OPadubkw==.Uu7ViqgKEt/dWvCyhI46q088PkAEJbnXKf3KPZjf9IEQQ+A8fojNoxw4wIbPX3aj",
          "Zvwv/+uhpFDznSX39Yjg==",
          null
        ],
        [
          "Invalid key characters",
          "m5ylFM6ndyOJA2OPadubkw==.Uu7ViqgKEt/dWvCyhI46q088PkAEJbnXKf3KPZjf9IEQQ+A8fojNoxw4wIbPX3aj",
          "Zvwv/%!(pFDznZ6SX39Yjg==",
          null
        ],
        [
          "Invalid body",
          "m5ylFM6ndyOJA2OPadubkw==.Uu7ViqgKEt/dWvCyhI46q0!*&()f3KPZjf9IEQQ+A8fojNoxw4wIbPX3aj",
          "Zvwv/+uhpFDznZ6SX28Yjg==",
          null
        ],
        [
          "Invalid iv length",
          "m5ylFM6ndyOPadubkw==.Uu7ViqgKEt/dWvCyhI46q088PkAEJbnXKf3KPZjf9IEQQ+A8fojNoxw4wIbPX3aj",
          "Zvwv/+uhpFDznZ6SX28Yjg==",
          null
        ],
        [
          "Invalid iv",
          "m5ylFM6*&(OJA2OPadubkw==.Uu7ViqgKEt/dWvCyhI46q088PkAEJbnXKf3KPZjf9IEQQ+A8fojNoxw4wIbPX3aj",
          "Zvwv/+uhpFDznZ6SX28Yjg==",
          null
        ],
        [
          "Missing delimiter",
          "m5ylFM6ndyOJA2OPadubkw==Uu7ViqgKEt/dWvCyhI46q088PkAEJbnXKf3KPZjf9IEQQ+A8fojNoxw4wIbPX3aj",
          "Zvwv/+uhpFDznZ6SX28Yjg==",
          null
        ],
        [
          "Corrupted payload",
          "fsa*(&(SF*&F&SF^SD&*FS&*6fsdkajfd",
          "Zvwv/+uhpFDznZ6SX28Yjg==",
          null
        ]
      ]
    }

""".trimIndent()*/