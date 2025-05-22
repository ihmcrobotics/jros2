Header header
std_msgs/Header header2

int32 my_int
string my_string # Test comment

int32[] unbounded_integer_array
int32[5] five_integers_array
int32[<=5] up_to_five_integers_array

string string_of_unbounded_size
string<=10 up_to_ten_characters_string

string[<=5] up_to_five_unbounded_strings
# Test header comment
string<=10[] unbounded_array_of_strings_up_to_ten_characters_each # Test comment
string<=10[<=5] up_to_five_strings_up_to_ten_characters_each

uint8 x 42
int16 y -2000
string full_name "John Doe"
int32[] samples [-200, -100, 0, 100, 200]
int32[5] samples2 [-200, -100, 0, 100, 200]

int32 X=123
int32 Y=-123
string FOO="foo"
string EXAMPLE='bar'