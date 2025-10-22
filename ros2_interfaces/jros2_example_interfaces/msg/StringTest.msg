# https://design.ros2.org/articles/interface_definition.html

string my_string1 "I heard \"Hello\""   # valid
string my_string2 "I heard "Hello""     # not valid
string my_string3 "I heard 'Hello'"     # valid
string my_string4 'I heard \'Hello\''   # valid
string my_string5 'I heard 'Hello''     # not valid
string my_string6 'I heard "Hello"'     # valid
