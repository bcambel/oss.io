
public class convert {
  public static String convertHexToString(String hex)
   {

    StringBuilder sb = new StringBuilder();
    StringBuilder temp = new StringBuilder();

    // 49204c6f7665204a617661 split into two characters 49, 20, 4c...
    for (int i = 0; i < hex.length() - 1; i += 2)
    {

     // grab the hex in pairs
     String output = hex.substring(i, (i + 2));


     // convert hex to decimal
     int decimal = Integer.parseInt(output, 16);

     System.out.println(output + (char) decimal);
     // convert the decimal to character
     sb.append((char) decimal);

     temp.append(decimal);
    }

    return sb.toString();
   }

   public static void main(String[] args){
    System.out.println( convert.convertHexToString(args[0]));
   }


 }